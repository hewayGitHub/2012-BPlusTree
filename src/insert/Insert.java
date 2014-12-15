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
 * �ڵ�Ҫ����Υ��״̬������������ڿɽ��ܷ�Χ֮����Ŀ��Ԫ�ء�
 * 1,���ȣ�����Ҫ�������еĽڵ��λ�á����Ű�ֵ��������ڵ��С�
 * 2,���û�нڵ㴦��Υ��״̬���������
 * 3,���ĳ���ڵ��й���Ԫ�أ����������Ϊ�����ڵ㣬ÿ��������С��Ŀ��Ԫ�ء�
 * �����ϵݹ����ϼ����������ֱ��������ڵ㣬������ڵ㱻���ѣ��򴴽�һ���¸��ڵ㡣
 * Ϊ��ʹ��������Ԫ�ص���С�������Ŀ���͵ı���ѡ��Ϊʹ��С����С���������һ�롣
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
		//������ڲ��ڵ㣬һ��Ҫע������ҽڵ�Ĵ���
		if (curPage.pageHeader.isZeroData) {
			int insertIndex = curPage.findInsertIndex(data.key);
			
			if (insertIndex <= curPage.pageHeader.cellNum) {
				Cell tempCell = curPage.readIndexCell(insertIndex);
				
				return insert(indexFileAccess.readPage(tempCell.leftChild), data);
			} else {
				
				return insert(indexFileAccess.readPage(curPage.pageHeader.rightMostP), data);
			}
		}
		
		//�����Ҷ�ӽ��
		if (curPage.pageHeader.isLeaf) {
			int insertIndex = curPage.findInsertIndex(data.key);
			
			//��ֹ�����key�Ѿ����ڡ�insertindex��ʾ��С�ڵ�insertindex��key����ôֻҪ������Ƿ����ǰһ��key���ɡ�
			if (insertIndex>1) {
				Cell tempCell = curPage.readIndexCell(insertIndex-1);
				tempCell.setKeyString();
				if (tempCell.keyString.equals(data.key)) {
					//JOptionPane.showMessageDialog(null, "The key " + data.key + " has already existed...");
					return false;
				}
			}
			
			int freeCellSize = curPage.pageHeader.firstCellOffset - Page.PAGEHEADER_SIZE - curPage.pageHeader.cellNum*2;
			//cellpointer���������δ����ռ��У��ռ䲻���ͱ�����ѡ����Կ�������ҳ��
			if (curPage.pageHeader.cellNum >= MAX_CELLNUM || freeCellSize < 2) {
				SplitInfo insertInfo = new SplitInfo(insertIndex, data.key, data.dataPosition);
				split(curPage, insertInfo);
				return true;
			}
			
			//������п��пռ�ʹ�����п�
			if (curPage.saveToFreeBlock(insertIndex, data.toCell())) {
				return true;
			} else {
				//û�п��õĿ��п飬��page��δ����ռ�Ĵ�СҲ��������Ҫ���ѡ����򣬴���δ����ռ�
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
		//����ǰҳ�����Ϊ����ҳ��
		SplitInfo upInsertInfo = splitIntoTwo(curPage, insertInfo);
		
		//����ǰҳ��Ϊ��ҳ��ʱ��Ҫ�½�һ����ҳ�档
		rootPage = indexFileAccess.readRootPage();
		if (curPage.pageHeader.parentPosition == 0) {
			//����һ���µ�ҳ��,Ҳ������B���г��ֵĵ�һ���ڲ��ڵ�
			Page newRootPage = indexFileAccess.getFreePage(0, true, false, false);
			
			//�޸�ԭ�����ڵ�ĸ��ڵ�ͷ��ѵ�ҳ��ĸ��ڵ�
		    rootPage.pageHeader.parentPosition = newRootPage.pageHeader.pagePosition;
		    rootPage.pageHeader.writeParentPosition();
		    
		    indexFileMappedByteBuffer.putInt(upInsertInfo.pagePosition+12, newRootPage.pageHeader.pagePosition);
		    
			//�����ѵ�ҳ��ĵ�ַд�����ҽڵ�
			newRootPage.pageHeader.rightMostP = upInsertInfo.pagePosition;
			newRootPage.pageHeader.writeRightMostP();
			
			//д��һ��cell��leftChildΪԭ���ĸ��ڵ��λ�ã�keyΪ�ϴ���insertKey�������ϴ��ķ�����Ϣ������ϡ�
			SplitInfo tempInfo = upInsertInfo;
			tempInfo.pagePosition = rootPage.pageHeader.pagePosition;
			newRootPage.saveToUnallocatedSpace(1, tempInfo.toCell());
			
			//�޸������ļ����ļ�ͷ���޸ĸ��ڵ�
		    indexFileAccess.indexFileHeader.rootPagePosition = newRootPage.pageHeader.pagePosition;
		    indexFileAccess.indexFileHeader.writeRootpagePosition();
			
		    return;
		}
		
		/*
		 * ���򽫸÷�����Ϣ�����ϴ���
		 * 1��upInsertInfo�в���ڵ�Ϊָ������Ҫ��
		 * 2����ǰ�ڵ�ĸ��ڵ���Ҫ��
		 */
		Page parentPage = indexFileAccess.readPage(curPage.pageHeader.parentPosition);
		int insertIndex = parentPage.findInsertIndex(upInsertInfo.insertKey);
		upInsertInfo.insertIndex = insertIndex;
		
		/*
		 * �����ڲ��ڵ㣬upInsertInfo�е�pagePostion��key�ұ� ��ָ�룬���䲻��ֱ��ת��Ϊһ��cell��
		 * ͨ������pagePostion�Ͳ����Cell���ӣ��Ϳ���ֱ�Ӳ�����cell�ˡ�
		 * ��ô��split���ϴ��ķ�����Ϣ�Ͷ���cell�ˡ�
		 */
		if (insertIndex > parentPage.pageHeader.cellNum) {
			int oldRightMostP = parentPage.pageHeader.rightMostP;
			
			parentPage.pageHeader.rightMostP = upInsertInfo.pagePosition;
			parentPage.pageHeader.writeRightMostP();
			
			upInsertInfo.pagePosition = oldRightMostP;
		} else {
			//�޸�tempindex��cell��leftChildΪinsertInfo�е�pagePostion�������Ǵ˴���cell
			Cell newCell = parentPage.readIndexCell(insertIndex);
			int oldLeftChild = newCell.leftChild;
			
			newCell.leftChild = upInsertInfo.pagePosition;
			parentPage.coverCell(insertIndex, newCell);
			
			upInsertInfo.pagePosition = oldLeftChild;
		}
		
		int freeCellSize = parentPage.pageHeader.firstCellOffset - Page.PAGEHEADER_SIZE - parentPage.pageHeader.cellNum*2;
		//cellpointer���������δ����ռ��У��ռ䲻���ͱ�����ѡ����Կ�������ҳ��
		if (parentPage.pageHeader.cellNum >= MAX_CELLNUM || freeCellSize < 2) {
			split(parentPage, upInsertInfo);
			return;
		}
		
		//������п��пռ�ʹ�����п�
		if (parentPage.saveToFreeBlock(insertIndex, upInsertInfo.toCell())) {
			return;
		} else {
			//û�п��õĿ��п飬��page��δ����ռ�Ĵ�СҲ��������Ҫ���ѡ����򣬴���δ����ռ�
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
	 * ����ǰҳ����Ϊ����cell������ȵģ��������µķ�����Ϣ
	 * @param input�����뵱ǰҳ�ķ�����Ϣ����Ϊһ��cell����pagePostion��key�����ָ�롣
	 * @return ���ϴ��͵ķ�����Ϣ
	 */
	public static SplitInfo splitIntoTwo(Page curPage, SplitInfo insertInfo) {
		//������ҳ�棬����ʼ��ҳͷ����ҳ������ͺ͵�ǰҳ��һ����
		Page newPage = indexFileAccess.getFreePage(curPage.pageHeader.parentPosition, curPage.pageHeader.isZeroData, 
				curPage.pageHeader.isLeaf, curPage.pageHeader.isIntKey);
		
		//����ӵڼ�����Ԫ��ʼ���ѣ�splitCellNum�������ұߵĽڵ�ֵ���ҳ��
		int splitCellNum = curPage.pageHeader.cellNum/2+1;
		if (insertInfo.insertIndex > splitCellNum) {
			splitCellNum++;
		}
		Cell splitCell = curPage.readIndexCell(splitCellNum);
		
		//ͳ�Ʒ��ѵ���Ϣ
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
		 * �����Ҷ�ӽڵ㡣���ÿ������ҽڵ㣬��cell��Ϊ���ݣ�д������ҳ�漴�ɡ�
		 */
		if (curPage.pageHeader.isLeaf) {
			//�����ѵ㼰���ұߵ�cellд��newPage��δ����ռ���
			Cell tempCell = null;
			for (int i = splitCellNum,tempIndex = 1; i <= curPage.pageHeader.cellNum; i++,tempIndex++) {
				tempCell = curPage.readIndexCell(i);
				newPage.saveToUnallocatedSpace(tempIndex, tempCell);
			}
			
			//Ϊ�˱����������Ŀ��п飬�����ѵ�֮ǰ��cell����Ȼ��д�뵱ǰҳ���������һ��Ԫ�ؿ�ʼ�档
			Cell[] leftCells = new Cell[splitCellNum];
			for (int i = 1; i < splitCellNum; i++) {
				leftCells[i] = curPage.readIndexCell(i);
			}
			//��յ�ǰҳ������cell
			curPage.clearAllCell();
			
			for (int i = 1; i < splitCellNum; i++) {
				curPage.saveToUnallocatedSpace(i, leftCells[i]);
			}
			
			//������ɷ��ѵĽڵ㡣������ڷ��ѽڵ������������ұߡ����С�ڵ��ڣ�������ߡ�
			//ע�⣺�����ұ�ʱ�������λ������insertInfo.insertIndex-splitCellNum+1��
			if (insertInfo.insertIndex > splitCellNum) {
				newPage.saveToUnallocatedSpace(insertInfo.insertIndex-splitCellNum+1, insertInfo.toCell());
			} else {
				curPage.saveToUnallocatedSpace(insertInfo.insertIndex, insertInfo.toCell());
			}
			
			//����һ�㷵�ط�����Ϣ���������ѽڵ��key����ҳ��ĵ�ַ
			splitCell.setKeyString();
			return new SplitInfo(-1, splitCell.keyString, newPage.pageHeader.pagePosition);
		}
		
		/*
		 * ������ڲ��ڵ㡣
		 * 1�����ѵ��cell�����ϴ������ô���������ҳ�С�
		 * 2����Ҫ�������ҽڵ㡣���ҳ������ҽڵ��Ƿ��ѵ��leftChild���ұ�Ҳ������ҽڵ��ǵ�ǰҳ�����ҽڵ㡣
		 * 3���ڲ��ڵ㱻����֮���亢�ӽڵ�ĸ��׽ڵ�Ӧ�÷����仯��
		 */
		if (curPage.pageHeader.isZeroData) {
			//�����ѵ��ұߵ�cellд��newPage��δ����ռ��С����ѽڵ㱾����д�롣
			Cell tempCell = null;
			for (int i = splitCellNum+1,tempIndex = 1; i <= curPage.pageHeader.cellNum; i++,tempIndex++) {
				tempCell = curPage.readIndexCell(i);
				newPage.saveToUnallocatedSpace(tempIndex, tempCell);
				
				//�޸Ľڵ��Ӧ�ӽڵ�ĸ��ڵ���Ϣ
				indexFileMappedByteBuffer.putInt(tempCell.leftChild+12, newPage.pageHeader.pagePosition);
			}
			//����ǰҳ������ָ��д����ҳ�У����޸�����ָ��ĸ��׽ڵ�ĵ�ַ
			newPage.pageHeader.rightMostP = curPage.pageHeader.rightMostP;
			newPage.pageHeader.writeRightMostP();
			indexFileMappedByteBuffer.putInt(curPage.pageHeader.rightMostP+12, newPage.pageHeader.pagePosition);
			
			//�����ѵ�֮ǰ��cell����Ȼ��д�뵱ǰҳ�����޸ĵ�ǰҳ�����ҽڵ�
			Cell[] leftCells = new Cell[splitCellNum];
			for (int i = 1; i < splitCellNum; i++) {
				leftCells[i] = curPage.readIndexCell(i);
			}
			//��յ�ǰҳ������cell
			curPage.clearAllCell();
			
			for (int i = 1; i < splitCellNum; i++) {
				curPage.saveToUnallocatedSpace(i, leftCells[i]);
			}
			//�޸ĵ�ǰҳ�����ҽڵ�
			curPage.pageHeader.rightMostP = splitCell.leftChild;
			curPage.pageHeader.writeRightMostP();
			
			/*
			 * ��split�ĺ������Ѿ�����insertInfo��ת��Ϊcell�ˣ���pagepositon��key�����ָ�롣
			 * ע��Ҫȷ������ڵ���ӽڵ�ĸ���ָ������ȷ�ġ�
			 */
			if (insertInfo.insertIndex > splitCellNum) {
				indexFileMappedByteBuffer.putInt(insertInfo.pagePosition+12, newPage.pageHeader.pagePosition);
				
				newPage.saveToUnallocatedSpace(insertInfo.insertIndex-splitCellNum, insertInfo.toCell());
			} else {
				indexFileMappedByteBuffer.putInt(insertInfo.pagePosition+12, curPage.pageHeader.pagePosition);
				curPage.saveToUnallocatedSpace(insertInfo.insertIndex, insertInfo.toCell());
			}
			
			//����һ�㷵�ط�����Ϣ���������ѽڵ��key����ҳ��ĵ�ַ
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
