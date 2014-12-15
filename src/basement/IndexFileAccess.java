package basement;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import javax.swing.JOptionPane;

/**
 * @author heway
 *实现索引文件的底层接口。
 *1，操作文件头，修改文件头的信息。
 *2，操作和维护空闲页表，申请、读取和删除页面。
 *
 *注意：
 *1，空闲页面：在每一个空闲页首部存入下一个空闲页的页号，为0表示没有下一页。freelist head page指向空闲页的第一个页面。
**   OFFSET   SIZE    DESCRIPTION
**      0      16     Header string: "hqlite format 1"
**     16       2     Page size in bytes. 
**     18       1     Min embedded payload fraction
**     19       1     Min leaf payload fraction
**     20       4     freelist head page
**     24       4     Number of freelist pages in the file
**     28       2     Number of tables, i.e. root pages
**     30       4     root page position
*
*待扩展
**     30       1     table name size in bytes
**     31       *     table name
**     *        4     root page of the preceding table
*      *        4     File change counter
 */
public class IndexFileAccess {
	private static final int MAP_LENGTH = 0x1000000;//16M
	
	private static File indexFile;
	private RandomAccessFile indexRandomAccessFile;
	private FileChannel indexFileChannel;
	public static MappedByteBuffer indexFileMappedByteBuffer;
	
	public static IndexFileHeader indexFileHeader = new IndexFileHeader();
	public static Page rootPage = null;
	
	public static class IndexFileHeader {
		//文件头各阈值的默认值
		public static final byte[] HQLITE_FOMAT = "hqlite format 1\n".getBytes();
		public static final short PAGE_SIZE = 512;
		public static final byte MIN_EMBEDDED_PAYLOAD = 126;
		public static final byte MIN_LEAF_PAYLOAD = 126;
		public static final int FREELIST_HEAD = 2;//页面一放文件头
		public static final int FREEPAGE_NUM = MAP_LENGTH/512-1;//页面一放文件头
		public static final short TABLE_NUM = 0;
		public static final int ROOTPAGE_POSITION = 512;
		
		//初始化为默认值
		public short pageSize = PAGE_SIZE;
		public byte minEmbeddedPayload = MIN_EMBEDDED_PAYLOAD;
		public byte minLeafPayload = MIN_LEAF_PAYLOAD;
		public int freelistHead = FREELIST_HEAD;
		public int freepageNum = FREEPAGE_NUM;
		public short tableNum = TABLE_NUM;
		public int rootPagePosition = ROOTPAGE_POSITION;
		
		/**
		 * 将freelist的表头和页数写入索引文件文件头
		 */
		public void writeFreelist() {
			indexFileMappedByteBuffer.putInt(20, indexFileHeader.freelistHead);
			indexFileMappedByteBuffer.putInt(24, indexFileHeader.freepageNum);
		}
		
		public void writeTableNum() {
			indexFileMappedByteBuffer.putShort(28, indexFileHeader.tableNum);
		}
		
		public int readRootpagePostion() {
			return indexFileMappedByteBuffer.getInt(30);
		}
		
		public void writeRootpagePosition() {
			indexFileMappedByteBuffer.putInt(30, indexFileHeader.rootPagePosition);
		}
		
		@Override
		public String toString() {
			return "File[\n"
					+"pagesize:"+indexFileHeader.pageSize+"\nminEmbeddedPayload:"+indexFileHeader.minEmbeddedPayload
					+"\nminLeafPayload:"+indexFileHeader.minLeafPayload+"\nfreelistHead:"+indexFileHeader.freelistHead
					+"\nfreepageNum:"+indexFileHeader.freepageNum+"\ntableNum:"+indexFileHeader.tableNum+"\nrootPage:"
					+indexFileHeader.rootPagePosition
					+"\n]File";
		}
	}
	
	/**
	 * 新建索引文件".\\db\\heway.hqlite"，初始化文件头和空闲页表
	 */
	private void creatIndexFile() {
		try {
			indexFile.createNewFile();
			RandomAccessFile tempRandomAccessFile = new RandomAccessFile(indexFile, "rw");
			tempRandomAccessFile.setLength(MAP_LENGTH);
			
			FileChannel fc = tempRandomAccessFile.getChannel();
			ByteBuffer tempBuffer = ByteBuffer.allocate(IndexFileHeader.PAGE_SIZE);
			
			//写文件头
			tempBuffer.put(IndexFileHeader.HQLITE_FOMAT);
			tempBuffer.putShort(IndexFileHeader.PAGE_SIZE);
			tempBuffer.put(IndexFileHeader.MIN_EMBEDDED_PAYLOAD);
			tempBuffer.put(IndexFileHeader.MIN_LEAF_PAYLOAD);
			tempBuffer.putInt(IndexFileHeader.FREELIST_HEAD);
			tempBuffer.putInt(IndexFileHeader.FREEPAGE_NUM);
			tempBuffer.putShort(IndexFileHeader.TABLE_NUM);
			tempBuffer.putInt(IndexFileHeader.ROOTPAGE_POSITION);
				
			tempBuffer.flip();
			fc.write(tempBuffer);
			
			//初始化空闲页表。每个空闲页中放下一个空闲页的页号，最后一个空闲页放置的页号为0.
			for (int pageNum = 2; pageNum < IndexFileHeader.FREEPAGE_NUM+1; pageNum++) {
				tempBuffer.clear();
				tempBuffer.putInt(pageNum+1);
				tempBuffer.flip();
				fc.write(tempBuffer, (pageNum-1)*IndexFileHeader.PAGE_SIZE);
			}
			//设置最后一个空闲页的页号为0
			tempBuffer.clear();
			tempBuffer.putInt(0);
			tempBuffer.flip();
			fc.write(tempBuffer, IndexFileHeader.FREEPAGE_NUM*IndexFileHeader.PAGE_SIZE);
			
			fc.close();	
			tempRandomAccessFile.close();
		} catch (IOException e) {
			// TODO: handle exception
			JOptionPane.showMessageDialog(null, "Cannot creat a index file...");
			System.exit(-1);
		}
	}
	
	/**
	 * 读取索引文件文件头，确保indexFileHeader中的值为最新值。
	 */
	private void readIndexFileHead() {
		indexFileMappedByteBuffer.position(16);
		
		indexFileHeader.pageSize = indexFileMappedByteBuffer.getShort();
		indexFileHeader.minEmbeddedPayload = indexFileMappedByteBuffer.get();
		indexFileHeader.minLeafPayload = indexFileMappedByteBuffer.get();
		indexFileHeader.freelistHead = indexFileMappedByteBuffer.getInt();
		indexFileHeader.freepageNum = indexFileMappedByteBuffer.getInt();
		indexFileHeader.tableNum = indexFileMappedByteBuffer.getShort();
		indexFileHeader.rootPagePosition = indexFileMappedByteBuffer.getInt();
	}
	
	/**
	 * 打开索引文件，获得B+树的根节点
	 * @return B+树的根节点 Page
	 */
	public Page open() {
		//打开索引文件
		try {
			indexFile = new File(".\\db\\heway.hqlite");
			//用于调试阶段，每次都建立新的索引文件
			indexFile.delete();
			//如果索引文件不存在，则新建索引文件
			if (!indexFile.exists()) {
				creatIndexFile();
			}
			
			//初始化索引文件操作接口
			indexRandomAccessFile = new RandomAccessFile(indexFile, "rw");
			indexFileChannel = indexRandomAccessFile.getChannel();
			indexFileMappedByteBuffer = indexFileChannel.map(FileChannel.MapMode.READ_WRITE, 0, MAP_LENGTH);
	
			//验证该文件是否为索引文件
			byte[] hqlite = new byte[16];
			indexFileMappedByteBuffer.get(hqlite);	
			if (!Arrays.equals(hqlite, IndexFileHeader.HQLITE_FOMAT)) {
				JOptionPane.showMessageDialog(null, "not equal...");
				indexFile.delete();
				creatIndexFile();
			}
			
			//读取索引文件头
			readIndexFileHead();
			
			//读取根节点。如果tableNum为0，则新建一个没有任何节点的根节点。
			if (indexFileHeader.tableNum == 0) {
				rootPage = getFreePage(0, false, true, false);
				
				indexFileHeader.tableNum = 1;
				indexFileHeader.writeTableNum();
			} else {
				rootPage = readRootPage();
			}
			
			return rootPage;
		} catch (Exception e) {
			// TODO: handle exception
			JOptionPane.showMessageDialog(null, "Fail to open hqlite...");
			System.exit(-1);
			return null;
		}
	}
	
	/**
	 * 获得空闲页，并更新空闲页表的表头和空闲页数
	 * @param parentPosition
	 * @param isZeroData
	 * @param isLeaf
	 * @param isIntKey
	 * @return Page
	 */
	public Page getFreePage(int parentPosition, boolean isZeroData, boolean isLeaf, boolean isIntKey) {
		if (indexFileHeader.freepageNum == 0) {
			JOptionPane.showMessageDialog(null, "there is no free page left");
			System.exit(-1);
		}
		
		int oldHead = indexFileHeader.freelistHead;
		
		//读取空闲页链下一个页，修改空闲页表的表头和空闲页数，初始化返回页的页头
		indexFileHeader.freelistHead = indexFileMappedByteBuffer.getInt((oldHead-1)*IndexFileHeader.PAGE_SIZE);
		indexFileHeader.freepageNum--;
		indexFileHeader.writeFreelist();
		
		//初始化返回页
		Page newPage = new Page();	
		newPage.pageHeader.pagePosition = (oldHead-1)*IndexFileHeader.PAGE_SIZE;
		newPage.pageHeader.isZeroData = isZeroData;
		newPage.pageHeader.isLeaf = isLeaf;
		newPage.pageHeader.isIntKey = isIntKey;
		newPage.pageHeader.parentPosition = parentPosition;
		
		//初始化返回页的页头
		newPage.writePageHeader();
		
		return newPage;
	}
	
	/**
	 * 无任何页面指向CurPage，直接将其添入freePage的表头。
	 * 注意，空闲页表，每一个页面存储的是页号，不是页的文件position。
	 * @param curPage 无任何页面指向它。
	 */
	public void deletePage(Page curPage) {
		int oldHead = indexFileHeader.freelistHead;
		int curPageNo = curPage.pageHeader.pagePosition / IndexFileHeader.PAGE_SIZE;
		
		indexFileHeader.freelistHead = curPageNo;
		indexFileHeader.freepageNum++;
		indexFileHeader.writeFreelist();
		
		indexFileMappedByteBuffer.putInt(curPage.pageHeader.pagePosition, oldHead);
		
	}
	
	/**
	 * 读取并返回根页面，因为根页面会变化，所以在每次使用根页面的时候确保调用该函数
	 * @return Page
	 */
	public Page readRootPage() {
		int rootPagePosition = indexFileHeader.readRootpagePostion();
		return readPage(rootPagePosition);
	}
	
	/**
	 * 从特定外存位置读取一个page的header，并构建一个内存的Page实例
	 * @param position
	 * @return Page
	 */
	public Page readPage(int position) {
		Page page = new Page();
		
		indexFileMappedByteBuffer.position(position);
		
		byte flag = indexFileMappedByteBuffer.get();
		if ((flag & 0x01) != 0) {
			page.pageHeader.isIntKey = true;
		}
		if ((flag & 0x02) != 0) {
			page.pageHeader.isZeroData = true;
		}
		if ((flag & 0x04) != 0) {
			page.pageHeader.isLeaf = true;
		}
		page.pageHeader.firstFreeBlockOffset = indexFileMappedByteBuffer.getShort();
		page.pageHeader.cellNum = indexFileMappedByteBuffer.getShort();
		page.pageHeader.firstCellOffset = indexFileMappedByteBuffer.getShort();
		page.pageHeader.fragmentBytes = indexFileMappedByteBuffer.get();
		page.pageHeader.rightMostP = indexFileMappedByteBuffer.getInt();
		page.pageHeader.parentPosition = indexFileMappedByteBuffer.getInt();

		page.pageHeader.pagePosition = position;
		
		return page;
	}
	
	public void close() throws IOException {
		indexFileMappedByteBuffer.force();
		indexFileChannel.close();
		indexRandomAccessFile.close();
	}
	
	private static boolean isFirst = true;
	private static int pageIndex = 1;
	/**
	 * 输出当前页面和所有子页面的页头
	 * @param indexFileMBF
	 * @param keyType
	 * @throws Exception 
	 */
	public void showAllPage(Page curPage) throws Exception {
		PrintWriter out = null;
		
		try {
			if (isFirst) {
				out = new PrintWriter(".\\db\\allPage.txt");
				out.print("");
				out.close();
				isFirst = false;
			}
			
			out = new PrintWriter(new FileWriter(".\\db\\allPage.txt", true));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//输出当前页的页头
		out.println(pageIndex++ + " "+curPage.pageHeader);
		out.close();
		
		//输出当前页的所有Cell
		curPage.showAllCell(".\\db\\cell\\"+curPage.pageHeader.pagePosition);
		
		//如果是叶子节点，返回。否则，遍历。
		if (curPage.pageHeader.isLeaf) {
			//throw new Exception();
			return;
		}
		
		short cellPointer;
		Cell tempCell = null;
		Page tempPage = null;
		
		for (int i = 0; i < curPage.pageHeader.cellNum; i++) {
			cellPointer = indexFileMappedByteBuffer.getShort(curPage.pageHeader.pagePosition+Page.PAGEHEADER_SIZE+i*2);
			
			tempCell = curPage.readOffsetCell(cellPointer);
			tempPage = readPage(tempCell.leftChild);
			
			showAllPage(tempPage);
		}
	
		tempPage = readPage(curPage.pageHeader.rightMostP);
		showAllPage(tempPage);
	}
	
	public void showAllLeaf(Page curPage) throws Exception {
		PrintWriter out = null;
		
		try {
			if (isFirst) {
				out = new PrintWriter(".\\db\\allLeaf.txt");
				out.print("");
				out.close();
				isFirst = false;
			}
			
			out = new PrintWriter(new FileWriter(".\\db\\allLeaf.txt", true));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//输出当前页的页头
		if (curPage.pageHeader.isLeaf) {
			out.println(pageIndex++ + " "+curPage.pageHeader);
			out.close();
			//输出当前页的所有Cell
			curPage.showAllCell(".\\db\\leaf\\"+curPage.pageHeader.pagePosition);
			return;
		}
		out.close();
		
		short cellPointer;
		Cell tempCell = null;
		Page tempPage = null;
		
		for (int i = 0; i < curPage.pageHeader.cellNum; i++) {
			cellPointer = indexFileMappedByteBuffer.getShort(curPage.pageHeader.pagePosition+Page.PAGEHEADER_SIZE+i*2);
			
			tempCell = curPage.readOffsetCell(cellPointer);
			tempPage = readPage(tempCell.leftChild);
			
			showAllLeaf(tempPage);
		}
	
		tempPage = readPage(curPage.pageHeader.rightMostP);
		showAllLeaf(tempPage);
	}
	
	/**
	 * ---|3|
   |---|1|2|3|
   |---|4|5|
	 * @param out
	 */
	String outFileName = null;
	public void printBPlusTree(String fileName) {
		rootPage = readRootPage();
		outFileName = fileName;
		print(rootPage);
	}
	
	private void print(Page curPage) {
		//输出当前页的页头
		PrintWriter out;
		try {
			out = new PrintWriter(new FileWriter(outFileName, true));
			out.println(curPage.toString());
			out.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//如果是叶子节点，返回。否则，遍历。
		if (curPage.pageHeader.isLeaf) {
			return;
		}
		
		short cellPointer;
		Cell tempCell = null;
		Page tempPage = null;
		
		for (int i = 0; i < curPage.pageHeader.cellNum; i++) {
			cellPointer = indexFileMappedByteBuffer.getShort(curPage.pageHeader.pagePosition+Page.PAGEHEADER_SIZE+i*2);
			
			tempCell = curPage.readOffsetCell(cellPointer);
			tempPage = readPage(tempCell.leftChild);
			
			print(tempPage);
		}
	
		tempPage = readPage(curPage.pageHeader.rightMostP);
		print(tempPage);
	}
	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub
		PrintStream out = new PrintStream( 
				new BufferedOutputStream(
						new FileOutputStream(new File(".\\db\\testIndexFile.txt"))));
		
		IndexFileAccess main = new IndexFileAccess();
		
		//测试文件头是否初始成功
		out.println("----testing file header----");
		rootPage = main.open();
		out.println(main.indexFileHeader);
		out.println();
		
		//测试跟page是否初始成功
		out.println("----testing page initializing----");
		out.println(rootPage.pageHeader);
		out.println();
		
		//测试空闲页表
		out.println("----testing free list----");
		main.getFreePage(0, true, false, false);
		out.println(main.indexFileHeader);
		out.println();
		
		main.close();
		out.close();
	}
}
