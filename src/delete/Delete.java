package delete;


import java.nio.MappedByteBuffer;

import javax.swing.JOptionPane;

import seek.Seek;
import seek.SeekInfo;

import basement.Cell;
import basement.IndexFileAccess;
import basement.KeyType;
import basement.Page;

/**
 * 
 * @author heway
 *���ȣ�����Ҫɾ����ֵ�����ŴӰ������Ľڵ���ɾ�����ֵ��
*1,���û�нڵ㴦��Υ��״̬���������
*2,����ڵ㴦��Υ��״̬�������ֿ��������
*	a,�����ֵܽڵ㣬����ͬһ�����ڵ���ӽڵ㣬���԰�һ�����������ӽڵ�ת�Ƶ���ǰ�ڵ㣬����������Ϊ�Ϸ�״̬��
*	������������ڸ��ĸ��ڵ�������ֵܽڵ�ķ���ֵ֮���������
*	b,�����ֵܽڵ����ڴ��ڵͱ߽��϶�û�ж�����ӽڵ㡣����������°������ֵܽڵ�ϲ���һ����һ�Ľڵ��У��������ǵݹ鵽���ڵ��ϣ�
*	��Ϊ����ɾ����һ���ӽڵ㡣�����������ֱ����ǰ�ڵ��ǺϷ�״̬���ߵ�����ڵ㣬�����ϸ��ڵ���ӽڵ㱻�ϲ����Һϲ���Ľڵ��Ϊ�µĸ��ڵ㡣
 */
public class Delete {
	public static int MAX_CELLNUM = 3;
	
	public static KeyType dbKeyType = KeyType.INT;
	public static IndexFileAccess indexFileAccess; 
	public static MappedByteBuffer indexFileMappedByteBuffer;
	
	public static void initDelete(KeyType dbKeyType, IndexFileAccess indexFileAccess, MappedByteBuffer indexFileMappedByteBuffer) {
		Delete.dbKeyType = dbKeyType;
		Delete.indexFileAccess = indexFileAccess;
		Delete.indexFileMappedByteBuffer = indexFileMappedByteBuffer;
	}
	
	public static boolean delete(String key) {
		indexFileAccess.readRootPage();
		//��ʼ�������������ҵ�key���ڵ�Ҷ�ӽڵ�λ�á�
		Seek.initSeek(dbKeyType, indexFileAccess, indexFileMappedByteBuffer);
		SeekInfo whereKey = Seek.seek(key);
		
		//whereKeyΪnull��ʾ��key�����ڣ�����ɾ����key
		if (whereKey == null) {
			//JOptionPane.showMessageDialog(null, "In delete:["+key+"] not exists");
			return false;
		}
		
		delete(whereKey.key, whereKey.wherePage, whereKey.whereIndex);
		return true;
	}
	
	/**
	 * ɾ��whereKey��ǵ�key.
	 * 1,deleteIndex�ķ�ΧΪ1-cellNum������Ҷ�ӽڵ㣬��Ϊkey��Ӧ��cell�������ڸ÷�Χ�������ڲ��ڵ㣬��֤�˴��ݸ����deleteIndex�Ǹ÷�Χ��
	 * 2,ά�����ڵ㡣
	 * @param whereKey
	 */
	private static void delete(String deleteKey, int wherePage, int deleteIndex) {
		Page curPage = indexFileAccess.readPage(wherePage);
		
		//ֱ��ɾ����index��Ӧ��cell���ڲ��ڵ��п���cellNumΪ0��ֻ������ָ�롣
		curPage.deleteCell(deleteIndex);
		
		//�����ǰ�ڵ�Ϊ���ڵ����ǣ����������B+��ֻ��һ���ڵ㡣�������κδ������򣬵�cellNumΪ0ʱ���µĸ�ҳ�档
		if (curPage.pageHeader.parentPosition == 0) {
			if (curPage.pageHeader.isLeaf) {
				return;
			}else {
				if (curPage.pageHeader.cellNum == 0) {
					indexFileAccess.indexFileHeader.rootPagePosition = curPage.pageHeader.rightMostP;
					indexFileAccess.indexFileHeader.writeRootpagePosition();
					
					//�޸����ҽڵ�ĸ��׽ڵ�
					indexFileMappedByteBuffer.putInt(curPage.pageHeader.rightMostP+12, 0);
				}
				return;
			}
		}
		
		//�����ǰ�ڵ㴦��Υ��״̬���ƶ����ߺϲ�
		if (curPage.pageHeader.isLeaf && curPage.pageHeader.cellNum < (MAX_CELLNUM+1)/2
				|| curPage.pageHeader.isZeroData&&curPage.pageHeader.cellNum < MAX_CELLNUM/2) {
			
			Page parentPage = indexFileAccess.readPage(curPage.pageHeader.parentPosition);
			int insertIndex = parentPage.findInsertIndex(deleteKey);
			
			//��ȡ��ǰ�ڵ���ֵܽڵ㣬cellNum+1>=insertInde>=1
			Page leftBroPage = null, rightBroPage = null;
			Cell leftBroCell = null, rightBroCell = null;
			if (insertIndex > 1) {
				leftBroCell = parentPage.readIndexCell(insertIndex-1);
				leftBroPage = indexFileAccess.readPage(leftBroCell.leftChild);
			}
			if (insertIndex < curPage.pageHeader.cellNum) {
				rightBroCell = parentPage.readIndexCell(insertIndex+1);
				rightBroPage = indexFileAccess.readPage(rightBroCell.leftChild);
			} else {
				if (insertIndex == curPage.pageHeader.cellNum) {
					rightBroPage = indexFileAccess.readPage(parentPage.pageHeader.rightMostP);
				}
			}
			
			//Ҷ�ӽڵ㣺����Ƿ���Դ����ֵܽڵ��ƽڵ����cellNum+1>=insertIndex>1
			if (curPage.pageHeader.isLeaf && leftBroPage != null && 
					(leftBroPage.pageHeader.cellNum+curPage.pageHeader.cellNum) > MAX_CELLNUM) {
				int moveNum = (MAX_CELLNUM+1)/2 - curPage.pageHeader.cellNum;
				Cell tempCell = null;
				//ֱ���ú�����ʽ�����⣺��ҳ��һֱ�ڱ䣬��cellNum��ֵһֱ�ڱ䡣
				int leftCellNum = leftBroPage.pageHeader.cellNum;
				//�����ֵ����������һ����С��ԭ�������ݣ����ԣ����Ӵ�С���룬һֱ����indexΪ1�ĵط�
				for (int i = leftCellNum; i > leftCellNum-moveNum; i--) {
					tempCell = leftBroPage.readIndexCell(i);
					
					//�޸�����cell��leftChild��Ӧҳ��ĸ�ҳ��
					if (leftBroPage.pageHeader.isZeroData) {
						indexFileMappedByteBuffer.putInt(tempCell.leftChild+12, curPage.pageHeader.pagePosition);
					}
					
					curPage.writeCell(1, tempCell);
					leftBroPage.deleteCell(i);
				}
				//�޸ĸ��׽ڵ������ֵܶ�Ӧ��cell��keyֵ�����һ��tempCell������curPage����Сֵ������Ϊ��keyֵ��
				leftBroCell.key = tempCell.key;
				parentPage.coverCell(insertIndex-1, leftBroCell);
				
				return;
			}
			//Ҷ�ӽڵ�(���ҵ���)������Ƿ���Դ����ֵܽڵ���cell������cellNum+1>insertIndex>=1
			if (curPage.pageHeader.isLeaf && rightBroPage != null && 
					(rightBroPage.pageHeader.cellNum+curPage.pageHeader.cellNum) > MAX_CELLNUM) {
				int moveNum = (MAX_CELLNUM+1)/2 - curPage.pageHeader.cellNum;
				
				Cell tempCell = null;
				int oldCellNum = curPage.pageHeader.cellNum;
				//�����ֵ����������һ��������ԭ�������ݣ����ԣ�����С������룬һֱ����cellNum��λ�á�
				//ע�����ֵ�ɾ����Ԫ�������ײ�������λ������һֱ�ڱ䡣�������ɾ����
				for (int i = 1; i <= moveNum; i++) {
					tempCell = rightBroPage.readIndexCell(i);
					
					//�޸�����cell��leftChild��Ӧҳ��ĸ�ҳ��
					if (rightBroPage.pageHeader.isZeroData) {
						indexFileMappedByteBuffer.putInt(tempCell.leftChild+12, curPage.pageHeader.pagePosition);
					}
					
					curPage.writeCell(oldCellNum+i, tempCell);
				}
				for (int i = 1; i <= moveNum; i++) {
					rightBroPage.deleteCell(i);
				}
				
				//�޸ĸ��׽ڵ���curPage��Ӧ��cell��keyֵ�����һ��tempCell������curPage�����ֵ������Ϊ��keyֵ��
				Cell curCell = parentPage.readIndexCell(insertIndex);
				curCell.key = tempCell.key;
				parentPage.coverCell(insertIndex, curCell);
				
				return;
			}
			//�����ƶ�������Ҫ���Ǻϲ���
			//����ڵ�ϲ���cellNum+1>=insertIndex>1
			if (curPage.pageHeader.isLeaf && leftBroPage != null) {
				//������cellд�뵱ǰ�ڵ�
				Cell tempCell = null;
				for (int i = leftBroPage.pageHeader.cellNum; i >= 1; i--) {
					tempCell = leftBroPage.readIndexCell(i);
					
					//�޸�����cell��leftChild��Ӧҳ��ĸ�ҳ��
					if (leftBroPage.pageHeader.isZeroData) {
						indexFileMappedByteBuffer.putInt(tempCell.leftChild+12, curPage.pageHeader.pagePosition);
					}
					
					curPage.writeCell(1, tempCell);
				}
				
				
				//�޸ĸ��׽ڵ㣺ɾ����ڵ��ӦCell��v1 k1 v2  Ҫɾ��v1 k1����Ϊv1��Ӧ��page��cell����curPage���ˡ�	
				//��֤�˸��ڵ�ֻҪɾ��insertIndex��Ӧ��cell���ɣ�����֤���ϴ�����Ϣ��cell��
				leftBroCell.setKeyString();
				delete(leftBroCell.keyString, parentPage.pageHeader.pagePosition, insertIndex-1);
				
				//ɾ����ҳ��
				indexFileAccess.deletePage(leftBroPage);
				
				return;
			}
			
			//cellNum+1>insertIndex>=1
			if (curPage.pageHeader.isLeaf && rightBroPage != null) {
				//������cellд���ҽڵ�
				Cell tempCell = null;
				for (int i = curPage.pageHeader.cellNum; i >= 1; i--) {
					tempCell = curPage.readIndexCell(i);
					
					//�޸�����cell��leftChild��Ӧҳ��ĸ�ҳ��
					if (curPage.pageHeader.isZeroData) {
						indexFileMappedByteBuffer.putInt(tempCell.leftChild+12, rightBroPage.pageHeader.pagePosition);
					}
					
					rightBroPage.writeCell(1, tempCell);
				}
				
				//�޸ĸ��׽ڵ㣺ֱ��ɾ��curCell��v2 k2 v3������v2��Ӧ��page���������ݶ���v3��Ӧ��ҳ����
				//��֤�˸��ڵ�ֻҪɾ��insertIndex��Ӧ��cell���ɣ�����֤���ϴ�����Ϣ��cell��
				Cell curCell = parentPage.readIndexCell(insertIndex);
				curCell.setKeyString();
				delete(curCell.keyString, parentPage.pageHeader.pagePosition, insertIndex);
				
				//ɾ����ǰҳ�棺��������ҳ��ָ��ǰҳ�档
				indexFileAccess.deletePage(curPage);
				
				return;
			}
			
			//�ڲ��ڵ㣺����Ƿ���Դ����ֵܽڵ��ƽڵ����cellNum+1>=insertIndex>1
			if (curPage.pageHeader.isZeroData && leftBroPage != null && 
					(leftBroPage.pageHeader.cellNum+curPage.pageHeader.cellNum) > MAX_CELLNUM) {
				int moveNum = MAX_CELLNUM/2 - curPage.pageHeader.cellNum;
				
				//�������ֵܽڵ������ָ�빹��һ��cell
				byte[] minKey = leftBroCell.key;
				Cell rightMostCell = new Cell();
				rightMostCell.key = minKey;
				rightMostCell.keySize = leftBroCell.keySize;
				rightMostCell.leftChild = leftBroPage.pageHeader.rightMostP;
				
				curPage.writeCell(1, rightMostCell);
				indexFileMappedByteBuffer.putInt(rightMostCell.leftChild+12, curPage.pageHeader.pagePosition);
				
				Cell tempCell = null;
				//ֱ���ú�����ʽ�����⣺��ҳ��һֱ�ڱ䣬��cellNum��ֵһֱ�ڱ䡣
				int leftCellNum = leftBroPage.pageHeader.cellNum;
				//�����ֵ����������һ����С��ԭ�������ݣ����ԣ����Ӵ�С���룬һֱ����indexΪ1�ĵط�
				for (int i = leftCellNum; i > leftCellNum-moveNum+1; i--) {
					tempCell = leftBroPage.readIndexCell(i);
					
					//�޸�����ڵ�leftChild��Ӧҳ��ĸ��ڵ�
					indexFileMappedByteBuffer.putInt(tempCell.leftChild+12, curPage.pageHeader.pagePosition);

					curPage.writeCell(1, tempCell);
					leftBroPage.deleteCell(i);
				}
				//����cell��key���Ƶ����ڵ��У�����leftchild��������ָ��
				tempCell = leftBroPage.readIndexCell(leftCellNum-moveNum+1);
				indexFileMappedByteBuffer.putInt(tempCell.leftChild+12, curPage.pageHeader.pagePosition);
				
				leftBroPage.pageHeader.rightMostP = tempCell.leftChild;
				leftBroPage.pageHeader.writeRightMostP();
				
				leftBroCell.key = tempCell.key;
				parentPage.coverCell(insertIndex-1, leftBroCell);
				
				return;
			}
			//�ڲ��ڵ�(���ҵ���)������Ƿ���Դ����ֵܽڵ���cell������cellNum+1>insertIndex>=1
			if (curPage.pageHeader.isZeroData && rightBroPage != null && 
					(rightBroPage.pageHeader.cellNum+curPage.pageHeader.cellNum) > MAX_CELLNUM) {
				int moveNum = MAX_CELLNUM/2 - curPage.pageHeader.cellNum;
				
				//���õ�ǰ�ڵ������ָ�빹��һ��cell
				Cell curCell = parentPage.readIndexCell(insertIndex);
				Cell rightMostCell = new Cell();
				rightMostCell.key = curCell.key;
				rightMostCell.keySize = curCell.keySize;
				rightMostCell.leftChild = curPage.pageHeader.rightMostP;
				
				curPage.writeCell(curPage.pageHeader.cellNum, rightMostCell);
				indexFileMappedByteBuffer.putInt(rightMostCell.leftChild+12, curPage.pageHeader.pagePosition);
				
				Cell tempCell = null;
				int oldCellNum = curPage.pageHeader.cellNum;
				//�����ֵ����������һ��������ԭ�������ݣ����ԣ�����С������룬һֱ����cellNum��λ�á�
				//ע�����ֵ�ɾ����Ԫ�������ײ�������λ������һֱ�ڱ䡣�������ɾ����
				for (int i = 1; i < moveNum; i++) {
					tempCell = rightBroPage.readIndexCell(i);
					
					//�޸�����cell��leftChild��Ӧҳ��ĸ�ҳ��
					if (rightBroPage.pageHeader.isZeroData) {
						indexFileMappedByteBuffer.putInt(tempCell.leftChild+12, curPage.pageHeader.pagePosition);
					}
					
					curPage.writeCell(oldCellNum+i, tempCell);
				}
				
				//д�뵱ǰҳ������ָ��
				tempCell = rightBroPage.readIndexCell(moveNum);
				indexFileMappedByteBuffer.putInt(tempCell.leftChild+12, curPage.pageHeader.pagePosition);
				
				curPage.pageHeader.rightMostP = tempCell.leftChild;
				curPage.pageHeader.writeRightMostP();
				
				for (int i = 1; i <= moveNum; i++) {
					rightBroPage.deleteCell(i);
				}
				
				//�޸ĸ��׽ڵ���curPage��Ӧ��cell��keyֵ�����һ��tempCell������curPage�����ֵ������Ϊ��keyֵ��
				curCell.key = tempCell.key;
				parentPage.coverCell(insertIndex, curCell);
				
				return;
			}
			
			//�����ƶ�������Ҫ���Ǻϲ���
			//�ڲ��ڵ㣺����ڵ�ϲ�������ڵ��cell���뵱ǰ�ڵ㡣cellNum+1>=insertIndex>1
			if (curPage.pageHeader.isZeroData && leftBroPage != null) {
				//�������ֵܽڵ������ָ�빹��һ��cell
				byte[] minKey = leftBroCell.key;
				Cell rightMostCell = new Cell();
				rightMostCell.key = minKey;
				rightMostCell.keySize = leftBroCell.keySize;
				rightMostCell.leftChild = leftBroPage.pageHeader.rightMostP;
				
				curPage.writeCell(1, rightMostCell);
				indexFileMappedByteBuffer.putInt(rightMostCell.leftChild+12, curPage.pageHeader.pagePosition);
				
				//������cellд�뵱ǰ�ڵ�
				Cell tempCell = null;
				for (int i = leftBroPage.pageHeader.cellNum; i >= 1; i--) {
					tempCell = leftBroPage.readIndexCell(i);
					
					//�޸�����cell��leftChild��Ӧҳ��ĸ�ҳ��
					if (leftBroPage.pageHeader.isZeroData) {
						indexFileMappedByteBuffer.putInt(tempCell.leftChild+12, curPage.pageHeader.pagePosition);
					}
					
					curPage.writeCell(1, tempCell);
				}
				
				
				//�޸ĸ��׽ڵ㣺ɾ����ڵ��ӦCell��v1 k1 v2  Ҫɾ��v1 k1����Ϊv1��Ӧ��page��cell����curPage���ˡ�	
				//��֤�˸��ڵ�ֻҪɾ��insertIndex��Ӧ��cell���ɣ�����֤���ϴ�����Ϣ��cell��
				leftBroCell.setKeyString();
				delete(leftBroCell.keyString, parentPage.pageHeader.pagePosition, insertIndex-1);
				
				//ɾ����ҳ��
				indexFileAccess.deletePage(leftBroPage);
				
				return;
			}
			
			//�ڲ��ڵ㣺���ҽڵ�ϲ�������ǰ�ڵ��cell�����ҽڵ㡣cellNum+1>insertIndex>=1
			if (curPage.pageHeader.isZeroData && rightBroPage != null) {
				//���õ�ǰ�ڵ������ָ�빹��һ��cell
				Cell curCell = parentPage.readIndexCell(insertIndex);
				Cell rightMostCell = new Cell();
				rightMostCell.key = curCell.key;
				rightMostCell.keySize = curCell.keySize;
				rightMostCell.leftChild = curPage.pageHeader.rightMostP;
				
				rightBroPage.writeCell(curPage.pageHeader.cellNum, rightMostCell);
				indexFileMappedByteBuffer.putInt(rightMostCell.leftChild+12, rightBroPage.pageHeader.pagePosition);
				//������cellд���ҽڵ�
				Cell tempCell = null;
				for (int i = curPage.pageHeader.cellNum; i >= 1; i--) {
					tempCell = curPage.readIndexCell(i);
					
					//�޸�����cell��leftChild��Ӧҳ��ĸ�ҳ��
					if (curPage.pageHeader.isZeroData) {
						indexFileMappedByteBuffer.putInt(tempCell.leftChild+12, rightBroPage.pageHeader.pagePosition);
					}
					
					rightBroPage.writeCell(1, tempCell);
				}
				
				//�޸ĸ��׽ڵ㣺ֱ��ɾ��curCell��v2 k2 v3������v2��Ӧ��page���������ݶ���v3��Ӧ��ҳ����
				//��֤�˸��ڵ�ֻҪɾ��insertIndex��Ӧ��cell���ɣ�����֤���ϴ�����Ϣ��cell��
				curCell.setKeyString();
				delete(curCell.keyString, parentPage.pageHeader.pagePosition, insertIndex);
				
				//ɾ����ǰҳ�棺��������ҳ��ָ��ǰҳ�档
				indexFileAccess.deletePage(curPage);
				
				return;
			}
		}
	}
}
