package insert;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.MappedByteBuffer;

import javax.swing.JOptionPane;

import basement.Cell;
import basement.IndexFileAccess;
import basement.KeyType;
import basement.Page;
import main.InputDataNode;

/**
 * @author heway
 * 节点要处于违规状态，它必须包含在可接受范围之外数目的元素。
 * 1,首先，查找要插入其中的节点的位置。接着把值插入这个节点中。
 * 2,如果没有节点处于违规状态则处理结束。
 * 3,如果某个节点有过多元素，则把它分裂为两个节点，每个都有最小数目的元素。
 * 在树上递归向上继续这个处理直到到达根节点，如果根节点被分裂，则创建一个新根节点。
 * 为了使它工作，元素的最小和最大数目典型的必须选择为使最小数不小于最大数的一半。
 */
public class Insert {
	public static int MAX_CELLNUM = 3;
	
	public static KeyType dbKeyType = KeyType.INT;
	public static IndexFileAccess indexFileAccess; 
	public static MappedByteBuffer indexFileMappedByteBuffer;
	private static Page rootPage = null;
	
	public static void initInsert(KeyType dbKeyType, IndexFileAccess indexFileAccess, MappedByteBuffer indexFileMappedByteBuffer) {
		Insert.dbKeyType = dbKeyType;
		Insert.indexFileAccess = indexFileAccess;
		Insert.indexFileMappedByteBuffer = indexFileMappedByteBuffer;
	}
	
	public static boolean insert(InputDataNode data) {
		Cell dataCell = data.toCell();
		
		rootPage = indexFileAccess.readRootPage();
		
		if (rootPage.pageHeader.cellNum == 0) {
			rootPage.saveToUnallocatedSpace(1, dataCell);
			return true;
		}
		
		return insert(rootPage, data);
	}
	
	private static boolean insert(Page curPage, InputDataNode data) {
		//如果是内部节点，一定要注意对最右节点的处理
		if (curPage.pageHeader.isZeroData) {
			int insertIndex = curPage.findInsertIndex(data.key);
			
			if (insertIndex <= curPage.pageHeader.cellNum) {
				Cell tempCell = curPage.readIndexCell(insertIndex);
				
				return insert(indexFileAccess.readPage(tempCell.leftChild), data);
			} else {
				
				return insert(indexFileAccess.readPage(curPage.pageHeader.rightMostP), data);
			}
		}
		
		//如果是叶子结点
		if (curPage.pageHeader.isLeaf) {
			int insertIndex = curPage.findInsertIndex(data.key);
			
			//防止插入的key已经存在。insertindex表示其小于第insertindex个key，那么只要检查其是否等于前一个key即可。
			if (insertIndex>1) {
				Cell tempCell = curPage.readIndexCell(insertIndex-1);
				tempCell.setKeyString();
				if (tempCell.keyString.equals(data.key)) {
					//JOptionPane.showMessageDialog(null, "The key " + data.key + " has already existed...");
					return false;
				}
			}
			
			int freeCellSize = curPage.pageHeader.firstCellOffset - Page.PAGEHEADER_SIZE - curPage.pageHeader.cellNum*2;
			//cellpointer必须分配在未分配空间中，空间不够就必须分裂。可以考虑整理页。
			if (curPage.pageHeader.cellNum >= MAX_CELLNUM || freeCellSize < 2) {
				SplitInfo insertInfo = new SplitInfo(insertIndex, data.key, data.dataPosition);
				split(curPage, insertInfo);
				return true;
			}
			
			//如果空闲块有空间就存入空闲块
			if (curPage.saveToFreeBlock(insertIndex, data.toCell())) {
				return true;
			} else {
				//没有可用的空闲块，且page内未分配空间的大小也不够，需要分裂。否则，存入未分配空间
				if (freeCellSize<(Cell.FIXED_SIZE+data.keySize+2)) {
					SplitInfo insertInfo = new SplitInfo(insertIndex, data.key, data.dataPosition);
					split(curPage, insertInfo);
					return true;
				} else {
					curPage.saveToUnallocatedSpace(insertIndex, data.toCell());
					return true;
				}
			}
		}
		return true;
	}
	
	private static void split(Page curPage, SplitInfo insertInfo) {	
		//将当前页面分裂为两个页面
		SplitInfo upInsertInfo = splitIntoTwo(curPage, insertInfo);
		
		//当当前页面为根页面时，要新建一个根页面。
		rootPage = indexFileAccess.readRootPage();
		if (curPage.pageHeader.parentPosition == 0) {
			//申请一个新的页面,也是整个B树中出现的第一个内部节点
			Page newRootPage = indexFileAccess.getFreePage(0, true, false, false);
			
			//修改原来根节点的父节点和分裂的页面的父节点
		    rootPage.pageHeader.parentPosition = newRootPage.pageHeader.pagePosition;
		    rootPage.pageHeader.writeParentPosition();
		    
		    indexFileMappedByteBuffer.putInt(upInsertInfo.pagePosition+12, newRootPage.pageHeader.pagePosition);
		    
			//将分裂的页面的地址写入最右节点
			newRootPage.pageHeader.rightMostP = upInsertInfo.pagePosition;
			newRootPage.pageHeader.writeRightMostP();
			
			//写入一个cell，leftChild为原来的根节点的位置，key为上传的insertKey。到此上传的分裂信息处理完毕。
			SplitInfo tempInfo = upInsertInfo;
			tempInfo.pagePosition = rootPage.pageHeader.pagePosition;
			newRootPage.saveToUnallocatedSpace(1, tempInfo.toCell());
			
			//修改索引文件的文件头，修改根节点
		    indexFileAccess.indexFileHeader.rootPagePosition = newRootPage.pageHeader.pagePosition;
		    indexFileAccess.indexFileHeader.writeRootpagePosition();
			
		    return;
		}
		
		/*
		 * 否则将该分裂信息继续上传。
		 * 1，upInsertInfo中插入节点为指定，需要求。
		 * 2，当前节点的父节点需要求。
		 */
		Page parentPage = indexFileAccess.readPage(curPage.pageHeader.parentPosition);
		int insertIndex = parentPage.findInsertIndex(upInsertInfo.insertKey);
		upInsertInfo.insertIndex = insertIndex;
		
		/*
		 * 对于内部节点，upInsertInfo中的pagePostion是key右边 的指针，即其不能直接转化为一个cell。
		 * 通过交换pagePostion和插入点Cell左孩子，就可以直接插入新cell了。
		 * 那么，split是上传的分裂信息就都是cell了。
		 */
		if (insertIndex > parentPage.pageHeader.cellNum) {
			int oldRightMostP = parentPage.pageHeader.rightMostP;
			
			parentPage.pageHeader.rightMostP = upInsertInfo.pagePosition;
			parentPage.pageHeader.writeRightMostP();
			
			upInsertInfo.pagePosition = oldRightMostP;
		} else {
			//修改tempindex处cell的leftChild为insertInfo中的pagePostion，并覆盖此处的cell
			Cell newCell = parentPage.readIndexCell(insertIndex);
			int oldLeftChild = newCell.leftChild;
			
			newCell.leftChild = upInsertInfo.pagePosition;
			parentPage.coverCell(insertIndex, newCell);
			
			upInsertInfo.pagePosition = oldLeftChild;
		}
		
		int freeCellSize = parentPage.pageHeader.firstCellOffset - Page.PAGEHEADER_SIZE - parentPage.pageHeader.cellNum*2;
		//cellpointer必须分配在未分配空间中，空间不够就必须分裂。可以考虑整理页。
		if (parentPage.pageHeader.cellNum >= MAX_CELLNUM || freeCellSize < 2) {
			split(parentPage, upInsertInfo);
			return;
		}
		
		//如果空闲块有空间就存入空闲块
		if (parentPage.saveToFreeBlock(insertIndex, upInsertInfo.toCell())) {
			return;
		} else {
			//没有可用的空闲块，且page内未分配空间的大小也不够，需要分裂。否则，存入未分配空间
			if (freeCellSize<(Cell.FIXED_SIZE+upInsertInfo.keySize+2)) {
				split(parentPage, upInsertInfo);
				return;
			} else {
				parentPage.saveToUnallocatedSpace(insertIndex, upInsertInfo.toCell());
				return;
			}
		}
	}
	
	private static boolean isFirst = true;
	/**
	 * 将当前页分裂为两份cell个数相等的，并返回新的分裂信息
	 * @param input：输入当前页的分裂信息，其为一个cell，即pagePostion是key的左边指针。
	 * @return 往上传送的分裂信息
	 */
	public static SplitInfo splitIntoTwo(Page curPage, SplitInfo insertInfo) {
		//申请新页面，并初始化页头。新页面的类型和当前页面一样。
		Page newPage = indexFileAccess.getFreePage(curPage.pageHeader.parentPosition, curPage.pageHeader.isZeroData, 
				curPage.pageHeader.isLeaf, curPage.pageHeader.isIntKey);
		
		//计算从第几个单元开始分裂，splitCellNum处和其右边的节点分到新页面
		int splitCellNum = curPage.pageHeader.cellNum/2+1;
		if (insertInfo.insertIndex > splitCellNum) {
			splitCellNum++;
		}
		Cell splitCell = curPage.readIndexCell(splitCellNum);
		
		//统计分裂点信息
		PrintWriter outSplit = null;
		
		try {
			if (isFirst) {
				outSplit = new PrintWriter(".\\db\\splitPoints.txt");
				outSplit.print("");
				outSplit.close();
				isFirst = false;
			}
			
			outSplit = new PrintWriter(new FileWriter(".\\db\\splitPoints.txt", true));
			outSplit.println(insertInfo);
			outSplit.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		/*
		 * 如果是叶子节点。不用考虑最右节点，将cell分为两份，写入两个页面即可。
		 */
		if (curPage.pageHeader.isLeaf) {
			//将分裂点及其右边的cell写入newPage的未分配空间中
			Cell tempCell = null;
			for (int i = splitCellNum,tempIndex = 1; i <= curPage.pageHeader.cellNum; i++,tempIndex++) {
				tempCell = curPage.readIndexCell(i);
				newPage.saveToUnallocatedSpace(tempIndex, tempCell);
			}
			
			//为了避免产生过多的空闲块，将分裂点之前的cell读出然后写入当前页。从数组第一个元素开始存。
			Cell[] leftCells = new Cell[splitCellNum];
			for (int i = 1; i < splitCellNum; i++) {
				leftCells[i] = curPage.readIndexCell(i);
			}
			//清空当前页的所有cell
			curPage.clearAllCell();
			
			for (int i = 1; i < splitCellNum; i++) {
				curPage.saveToUnallocatedSpace(i, leftCells[i]);
			}
			
			//插入造成分裂的节点。如果大于分裂节点索引，插入右边。如果小于等于，插入左边。
			//注意：插入右边时，插入的位置索引insertInfo.insertIndex-splitCellNum+1。
			if (insertInfo.insertIndex > splitCellNum) {
				newPage.saveToUnallocatedSpace(insertInfo.insertIndex-splitCellNum+1, insertInfo.toCell());
			} else {
				curPage.saveToUnallocatedSpace(insertInfo.insertIndex, insertInfo.toCell());
			}
			
			//向上一层返回分裂信息。包括分裂节点的key和新页面的地址
			splitCell.setKeyString();
			return new SplitInfo(-1, splitCell.keyString, newPage.pageHeader.pagePosition);
		}
		
		/*
		 * 如果是内部节点。
		 * 1，分裂点的cell会往上传，不用存在这两个页中。
		 * 2，需要考虑最右节点。左边页面的最右节点是分裂点的leftChild。右边也变的最右节点是当前页的最右节点。
		 * 3，内部节点被分裂之后，其孩子节点的父亲节点应该发生变化。
		 */
		if (curPage.pageHeader.isZeroData) {
			//将分裂点右边的cell写入newPage的未分配空间中。分裂节点本身不用写入。
			Cell tempCell = null;
			for (int i = splitCellNum+1,tempIndex = 1; i <= curPage.pageHeader.cellNum; i++,tempIndex++) {
				tempCell = curPage.readIndexCell(i);
				newPage.saveToUnallocatedSpace(tempIndex, tempCell);
				
				//修改节点对应子节点的父节点信息
				indexFileMappedByteBuffer.putInt(tempCell.leftChild+12, newPage.pageHeader.pagePosition);
			}
			//将当前页的最右指针写入新页中，并修改最右指针的父亲节点的地址
			newPage.pageHeader.rightMostP = curPage.pageHeader.rightMostP;
			newPage.pageHeader.writeRightMostP();
			indexFileMappedByteBuffer.putInt(curPage.pageHeader.rightMostP+12, newPage.pageHeader.pagePosition);
			
			//将分裂点之前的cell读出然后写入当前页。并修改当前页的最右节点
			Cell[] leftCells = new Cell[splitCellNum];
			for (int i = 1; i < splitCellNum; i++) {
				leftCells[i] = curPage.readIndexCell(i);
			}
			//清空当前页的所有cell
			curPage.clearAllCell();
			
			for (int i = 1; i < splitCellNum; i++) {
				curPage.saveToUnallocatedSpace(i, leftCells[i]);
			}
			//修改当前页的最右节点
			curPage.pageHeader.rightMostP = splitCell.leftChild;
			curPage.pageHeader.writeRightMostP();
			
			/*
			 * 在split的函数中已经所有insertInfo都转换为cell了，即pagepositon是key的左边指针。
			 * 注意要确保插入节点的子节点的父亲指针是正确的。
			 */
			if (insertInfo.insertIndex > splitCellNum) {
				indexFileMappedByteBuffer.putInt(insertInfo.pagePosition+12, newPage.pageHeader.pagePosition);
				
				newPage.saveToUnallocatedSpace(insertInfo.insertIndex-splitCellNum, insertInfo.toCell());
			} else {
				indexFileMappedByteBuffer.putInt(insertInfo.pagePosition+12, curPage.pageHeader.pagePosition);
				curPage.saveToUnallocatedSpace(insertInfo.insertIndex, insertInfo.toCell());
			}
			
			//向上一层返回分裂信息。包括分裂节点的key和新页面的地址
			splitCell.setKeyString();
			return new SplitInfo(-1, splitCell.keyString, newPage.pageHeader.pagePosition);
		}
		
		return null;
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
