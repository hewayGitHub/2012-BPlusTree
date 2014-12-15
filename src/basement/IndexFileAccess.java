package basement;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import javax.swing.JOptionPane;

/**
 * @author heway
 *ʵ�������ļ��ĵײ�ӿڡ�
 *1�������ļ�ͷ���޸��ļ�ͷ����Ϣ��
 *2��������ά������ҳ�����롢��ȡ��ɾ��ҳ�档
 *
 *ע�⣺
 *1������ҳ�棺��ÿһ������ҳ�ײ�������һ������ҳ��ҳ�ţ�Ϊ0��ʾû����һҳ��freelist head pageָ�����ҳ�ĵ�һ��ҳ�档
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
*����չ
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
		//�ļ�ͷ����ֵ��Ĭ��ֵ
		public static final byte[] HQLITE_FOMAT = "hqlite format 1\n".getBytes();
		public static final short PAGE_SIZE = 512;
		public static final byte MIN_EMBEDDED_PAYLOAD = 126;
		public static final byte MIN_LEAF_PAYLOAD = 126;
		public static final int FREELIST_HEAD = 2;//ҳ��һ���ļ�ͷ
		public static final int FREEPAGE_NUM = MAP_LENGTH/512-1;//ҳ��һ���ļ�ͷ
		public static final short TABLE_NUM = 0;
		public static final int ROOTPAGE_POSITION = 512;
		
		//��ʼ��ΪĬ��ֵ
		public short pageSize = PAGE_SIZE;
		public byte minEmbeddedPayload = MIN_EMBEDDED_PAYLOAD;
		public byte minLeafPayload = MIN_LEAF_PAYLOAD;
		public int freelistHead = FREELIST_HEAD;
		public int freepageNum = FREEPAGE_NUM;
		public short tableNum = TABLE_NUM;
		public int rootPagePosition = ROOTPAGE_POSITION;
		
		/**
		 * ��freelist�ı�ͷ��ҳ��д�������ļ��ļ�ͷ
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
	 * �½������ļ�".\\db\\heway.hqlite"����ʼ���ļ�ͷ�Ϳ���ҳ��
	 */
	private void creatIndexFile() {
		try {
			indexFile.createNewFile();
			RandomAccessFile tempRandomAccessFile = new RandomAccessFile(indexFile, "rw");
			tempRandomAccessFile.setLength(MAP_LENGTH);
			
			FileChannel fc = tempRandomAccessFile.getChannel();
			ByteBuffer tempBuffer = ByteBuffer.allocate(IndexFileHeader.PAGE_SIZE);
			
			//д�ļ�ͷ
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
			
			//��ʼ������ҳ��ÿ������ҳ�з���һ������ҳ��ҳ�ţ����һ������ҳ���õ�ҳ��Ϊ0.
			for (int pageNum = 2; pageNum < IndexFileHeader.FREEPAGE_NUM+1; pageNum++) {
				tempBuffer.clear();
				tempBuffer.putInt(pageNum+1);
				tempBuffer.flip();
				fc.write(tempBuffer, (pageNum-1)*IndexFileHeader.PAGE_SIZE);
			}
			//�������һ������ҳ��ҳ��Ϊ0
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
	 * ��ȡ�����ļ��ļ�ͷ��ȷ��indexFileHeader�е�ֵΪ����ֵ��
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
	 * �������ļ������B+���ĸ��ڵ�
	 * @return B+���ĸ��ڵ� Page
	 */
	public Page open() {
		//�������ļ�
		try {
			indexFile = new File(".\\db\\heway.hqlite");
			//���ڵ��Խ׶Σ�ÿ�ζ������µ������ļ�
			indexFile.delete();
			//��������ļ������ڣ����½������ļ�
			if (!indexFile.exists()) {
				creatIndexFile();
			}
			
			//��ʼ�������ļ������ӿ�
			indexRandomAccessFile = new RandomAccessFile(indexFile, "rw");
			indexFileChannel = indexRandomAccessFile.getChannel();
			indexFileMappedByteBuffer = indexFileChannel.map(FileChannel.MapMode.READ_WRITE, 0, MAP_LENGTH);
	
			//��֤���ļ��Ƿ�Ϊ�����ļ�
			byte[] hqlite = new byte[16];
			indexFileMappedByteBuffer.get(hqlite);	
			if (!Arrays.equals(hqlite, IndexFileHeader.HQLITE_FOMAT)) {
				JOptionPane.showMessageDialog(null, "not equal...");
				indexFile.delete();
				creatIndexFile();
			}
			
			//��ȡ�����ļ�ͷ
			readIndexFileHead();
			
			//��ȡ���ڵ㡣���tableNumΪ0�����½�һ��û���κνڵ�ĸ��ڵ㡣
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
	 * ��ÿ���ҳ�������¿���ҳ��ı�ͷ�Ϳ���ҳ��
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
		
		//��ȡ����ҳ����һ��ҳ���޸Ŀ���ҳ��ı�ͷ�Ϳ���ҳ������ʼ������ҳ��ҳͷ
		indexFileHeader.freelistHead = indexFileMappedByteBuffer.getInt((oldHead-1)*IndexFileHeader.PAGE_SIZE);
		indexFileHeader.freepageNum--;
		indexFileHeader.writeFreelist();
		
		//��ʼ������ҳ
		Page newPage = new Page();	
		newPage.pageHeader.pagePosition = (oldHead-1)*IndexFileHeader.PAGE_SIZE;
		newPage.pageHeader.isZeroData = isZeroData;
		newPage.pageHeader.isLeaf = isLeaf;
		newPage.pageHeader.isIntKey = isIntKey;
		newPage.pageHeader.parentPosition = parentPosition;
		
		//��ʼ������ҳ��ҳͷ
		newPage.writePageHeader();
		
		return newPage;
	}
	
	/**
	 * ���κ�ҳ��ָ��CurPage��ֱ�ӽ�������freePage�ı�ͷ��
	 * ע�⣬����ҳ��ÿһ��ҳ��洢����ҳ�ţ�����ҳ���ļ�position��
	 * @param curPage ���κ�ҳ��ָ������
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
	 * ��ȡ�����ظ�ҳ�棬��Ϊ��ҳ���仯��������ÿ��ʹ�ø�ҳ���ʱ��ȷ�����øú���
	 * @return Page
	 */
	public Page readRootPage() {
		int rootPagePosition = indexFileHeader.readRootpagePostion();
		return readPage(rootPagePosition);
	}
	
	/**
	 * ���ض����λ�ö�ȡһ��page��header��������һ���ڴ��Pageʵ��
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
	 * �����ǰҳ���������ҳ���ҳͷ
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
		//�����ǰҳ��ҳͷ
		out.println(pageIndex++ + " "+curPage.pageHeader);
		out.close();
		
		//�����ǰҳ������Cell
		curPage.showAllCell(".\\db\\cell\\"+curPage.pageHeader.pagePosition);
		
		//�����Ҷ�ӽڵ㣬���ء����򣬱�����
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
		//�����ǰҳ��ҳͷ
		if (curPage.pageHeader.isLeaf) {
			out.println(pageIndex++ + " "+curPage.pageHeader);
			out.close();
			//�����ǰҳ������Cell
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
		//�����ǰҳ��ҳͷ
		PrintWriter out;
		try {
			out = new PrintWriter(new FileWriter(outFileName, true));
			out.println(curPage.toString());
			out.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//�����Ҷ�ӽڵ㣬���ء����򣬱�����
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
		
		//�����ļ�ͷ�Ƿ��ʼ�ɹ�
		out.println("----testing file header----");
		rootPage = main.open();
		out.println(main.indexFileHeader);
		out.println();
		
		//���Ը�page�Ƿ��ʼ�ɹ�
		out.println("----testing page initializing----");
		out.println(rootPage.pageHeader);
		out.println();
		
		//���Կ���ҳ��
		out.println("----testing free list----");
		main.getFreePage(0, true, false, false);
		out.println(main.indexFileHeader);
		out.println();
		
		main.close();
		out.close();
	}
}
