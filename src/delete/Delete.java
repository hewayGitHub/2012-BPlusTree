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
 *首先，查找要删除的值。接着从包含它的节点中删除这个值。
*1,如果没有节点处于违规状态则处理结束。
*2,如果节点处于违规状态则有两种可能情况：
*	a,它的兄弟节点，就是同一个父节点的子节点，可以把一个或多个它的子节点转移到当前节点，而把它返回为合法状态。
*	如果是这样，在更改父节点和两个兄弟节点的分离值之后处理结束。
*	b,它的兄弟节点由于处在低边界上而没有额外的子节点。在这种情况下把两个兄弟节点合并到一个单一的节点中，而且我们递归到父节点上，
*	因为它被删除了一个子节点。持续这个处理直到当前节点是合法状态或者到达根节点，在其上根节点的子节点被合并而且合并后的节点成为新的根节点。
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
		//初始化查找器，并找到key所在的叶子节点位置。
		Seek.initSeek(dbKeyType, indexFileAccess, indexFileMappedByteBuffer);
		SeekInfo whereKey = Seek.seek(key);
		
		//whereKey为null表示该key不存在，否则删除该key
		if (whereKey == null) {
			//JOptionPane.showMessageDialog(null, "In delete:["+key+"] not exists");
			return false;
		}
		
		delete(whereKey.key, whereKey.wherePage, whereKey.whereIndex);
		return true;
	}
	
	/**
	 * 删除whereKey标记的key.
	 * 1,deleteIndex的范围为1-cellNum。对于叶子节点，其为key对应的cell，所以在该范围。对于内部节点，保证了传递给其的deleteIndex是该范围。
	 * 2,维护父节点。
	 * @param whereKey
	 */
	private static void delete(String deleteKey, int wherePage, int deleteIndex) {
		Page curPage = indexFileAccess.readPage(wherePage);
		
		//直接删除该index对应的cell。内部节点有可能cellNum为0，只有最右指针。
		curPage.deleteCell(deleteIndex);
		
		//如果当前节点为根节点且是，则表明整颗B+树只有一个节点。不用做任何处理。否则，当cellNum为0时，新的根页面。
		if (curPage.pageHeader.parentPosition == 0) {
			if (curPage.pageHeader.isLeaf) {
				return;
			}else {
				if (curPage.pageHeader.cellNum == 0) {
					indexFileAccess.indexFileHeader.rootPagePosition = curPage.pageHeader.rightMostP;
					indexFileAccess.indexFileHeader.writeRootpagePosition();
					
					//修改最右节点的父亲节点
					indexFileMappedByteBuffer.putInt(curPage.pageHeader.rightMostP+12, 0);
				}
				return;
			}
		}
		
		//如果当前节点处于违规状态，移动或者合并
		if (curPage.pageHeader.isLeaf && curPage.pageHeader.cellNum < (MAX_CELLNUM+1)/2
				|| curPage.pageHeader.isZeroData&&curPage.pageHeader.cellNum < MAX_CELLNUM/2) {
			
			Page parentPage = indexFileAccess.readPage(curPage.pageHeader.parentPosition);
			int insertIndex = parentPage.findInsertIndex(deleteKey);
			
			//读取当前节点的兄弟节点，cellNum+1>=insertInde>=1
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
			
			//叶子节点：检测是否可以从左兄弟节点移节点过来cellNum+1>=insertIndex>1
			if (curPage.pageHeader.isLeaf && leftBroPage != null && 
					(leftBroPage.pageHeader.cellNum+curPage.pageHeader.cellNum) > MAX_CELLNUM) {
				int moveNum = (MAX_CELLNUM+1)/2 - curPage.pageHeader.cellNum;
				Cell tempCell = null;
				//直接用后面表达式的问题：左页面一直在变，即cellNum的值一直在变。
				int leftCellNum = leftBroPage.pageHeader.cellNum;
				//从左兄弟移入的数据一定都小于原本的数据，所以，按从大到小插入，一直插入index为1的地方
				for (int i = leftCellNum; i > leftCellNum-moveNum; i--) {
					tempCell = leftBroPage.readIndexCell(i);
					
					//修改移入cell的leftChild对应页面的父页面
					if (leftBroPage.pageHeader.isZeroData) {
						indexFileMappedByteBuffer.putInt(tempCell.leftChild+12, curPage.pageHeader.pagePosition);
					}
					
					curPage.writeCell(1, tempCell);
					leftBroPage.deleteCell(i);
				}
				//修改父亲节点中左兄弟对应的cell的key值。最后一个tempCell即移入curPage的最小值，可作为该key值。
				leftBroCell.key = tempCell.key;
				parentPage.coverCell(insertIndex-1, leftBroCell);
				
				return;
			}
			//叶子节点(从右到左)：检测是否可以从右兄弟节点移cell过来，cellNum+1>insertIndex>=1
			if (curPage.pageHeader.isLeaf && rightBroPage != null && 
					(rightBroPage.pageHeader.cellNum+curPage.pageHeader.cellNum) > MAX_CELLNUM) {
				int moveNum = (MAX_CELLNUM+1)/2 - curPage.pageHeader.cellNum;
				
				Cell tempCell = null;
				int oldCellNum = curPage.pageHeader.cellNum;
				//从右兄弟移入的数据一定都大于原本的数据，所以，按从小到大插入，一直插入cellNum的位置。
				//注意右兄弟删除的元素是在首部，所以位置索引一直在变。先添入后删除。
				for (int i = 1; i <= moveNum; i++) {
					tempCell = rightBroPage.readIndexCell(i);
					
					//修改移入cell的leftChild对应页面的父页面
					if (rightBroPage.pageHeader.isZeroData) {
						indexFileMappedByteBuffer.putInt(tempCell.leftChild+12, curPage.pageHeader.pagePosition);
					}
					
					curPage.writeCell(oldCellNum+i, tempCell);
				}
				for (int i = 1; i <= moveNum; i++) {
					rightBroPage.deleteCell(i);
				}
				
				//修改父亲节点中curPage对应的cell的key值。最后一个tempCell即移入curPage的最大值，可作为该key值。
				Cell curCell = parentPage.readIndexCell(insertIndex);
				curCell.key = tempCell.key;
				parentPage.coverCell(insertIndex, curCell);
				
				return;
			}
			//不能移动，即需要考虑合并了
			//和左节点合并。cellNum+1>=insertIndex>1
			if (curPage.pageHeader.isLeaf && leftBroPage != null) {
				//将所有cell写入当前节点
				Cell tempCell = null;
				for (int i = leftBroPage.pageHeader.cellNum; i >= 1; i--) {
					tempCell = leftBroPage.readIndexCell(i);
					
					//修改移入cell的leftChild对应页面的父页面
					if (leftBroPage.pageHeader.isZeroData) {
						indexFileMappedByteBuffer.putInt(tempCell.leftChild+12, curPage.pageHeader.pagePosition);
					}
					
					curPage.writeCell(1, tempCell);
				}
				
				
				//修改父亲节点：删除左节点对应Cell。v1 k1 v2  要删除v1 k1，因为v1对应的page的cell都在curPage中了。	
				//保证了父节点只要删除insertIndex对应的cell即可，即保证了上传的信息是cell。
				leftBroCell.setKeyString();
				delete(leftBroCell.keyString, parentPage.pageHeader.pagePosition, insertIndex-1);
				
				//删除左页面
				indexFileAccess.deletePage(leftBroPage);
				
				return;
			}
			
			//cellNum+1>insertIndex>=1
			if (curPage.pageHeader.isLeaf && rightBroPage != null) {
				//将所有cell写入右节点
				Cell tempCell = null;
				for (int i = curPage.pageHeader.cellNum; i >= 1; i--) {
					tempCell = curPage.readIndexCell(i);
					
					//修改移入cell的leftChild对应页面的父页面
					if (curPage.pageHeader.isZeroData) {
						indexFileMappedByteBuffer.putInt(tempCell.leftChild+12, rightBroPage.pageHeader.pagePosition);
					}
					
					rightBroPage.writeCell(1, tempCell);
				}
				
				//修改父亲节点：直接删除curCell。v2 k2 v3，其中v2对应的page的所有内容都在v3对应的页面中
				//保证了父节点只要删除insertIndex对应的cell即可，即保证了上传的信息是cell。
				Cell curCell = parentPage.readIndexCell(insertIndex);
				curCell.setKeyString();
				delete(curCell.keyString, parentPage.pageHeader.pagePosition, insertIndex);
				
				//删除当前页面：已无其他页面指向当前页面。
				indexFileAccess.deletePage(curPage);
				
				return;
			}
			
			//内部节点：检测是否可以从左兄弟节点移节点过来cellNum+1>=insertIndex>1
			if (curPage.pageHeader.isZeroData && leftBroPage != null && 
					(leftBroPage.pageHeader.cellNum+curPage.pageHeader.cellNum) > MAX_CELLNUM) {
				int moveNum = MAX_CELLNUM/2 - curPage.pageHeader.cellNum;
				
				//利用左兄弟节点的最右指针构造一个cell
				byte[] minKey = leftBroCell.key;
				Cell rightMostCell = new Cell();
				rightMostCell.key = minKey;
				rightMostCell.keySize = leftBroCell.keySize;
				rightMostCell.leftChild = leftBroPage.pageHeader.rightMostP;
				
				curPage.writeCell(1, rightMostCell);
				indexFileMappedByteBuffer.putInt(rightMostCell.leftChild+12, curPage.pageHeader.pagePosition);
				
				Cell tempCell = null;
				//直接用后面表达式的问题：左页面一直在变，即cellNum的值一直在变。
				int leftCellNum = leftBroPage.pageHeader.cellNum;
				//从左兄弟移入的数据一定都小于原本的数据，所以，按从大到小插入，一直插入index为1的地方
				for (int i = leftCellNum; i > leftCellNum-moveNum+1; i--) {
					tempCell = leftBroPage.readIndexCell(i);
					
					//修改移入节点leftChild对应页面的父节点
					indexFileMappedByteBuffer.putInt(tempCell.leftChild+12, curPage.pageHeader.pagePosition);

					curPage.writeCell(1, tempCell);
					leftBroPage.deleteCell(i);
				}
				//将该cell的key上移到父节点中，并将leftchild赋给最右指针
				tempCell = leftBroPage.readIndexCell(leftCellNum-moveNum+1);
				indexFileMappedByteBuffer.putInt(tempCell.leftChild+12, curPage.pageHeader.pagePosition);
				
				leftBroPage.pageHeader.rightMostP = tempCell.leftChild;
				leftBroPage.pageHeader.writeRightMostP();
				
				leftBroCell.key = tempCell.key;
				parentPage.coverCell(insertIndex-1, leftBroCell);
				
				return;
			}
			//内部节点(从右到左)：检测是否可以从右兄弟节点移cell过来，cellNum+1>insertIndex>=1
			if (curPage.pageHeader.isZeroData && rightBroPage != null && 
					(rightBroPage.pageHeader.cellNum+curPage.pageHeader.cellNum) > MAX_CELLNUM) {
				int moveNum = MAX_CELLNUM/2 - curPage.pageHeader.cellNum;
				
				//利用当前节点的最右指针构造一个cell
				Cell curCell = parentPage.readIndexCell(insertIndex);
				Cell rightMostCell = new Cell();
				rightMostCell.key = curCell.key;
				rightMostCell.keySize = curCell.keySize;
				rightMostCell.leftChild = curPage.pageHeader.rightMostP;
				
				curPage.writeCell(curPage.pageHeader.cellNum, rightMostCell);
				indexFileMappedByteBuffer.putInt(rightMostCell.leftChild+12, curPage.pageHeader.pagePosition);
				
				Cell tempCell = null;
				int oldCellNum = curPage.pageHeader.cellNum;
				//从右兄弟移入的数据一定都大于原本的数据，所以，按从小到大插入，一直插入cellNum的位置。
				//注意右兄弟删除的元素是在首部，所以位置索引一直在变。先添入后删除。
				for (int i = 1; i < moveNum; i++) {
					tempCell = rightBroPage.readIndexCell(i);
					
					//修改移入cell的leftChild对应页面的父页面
					if (rightBroPage.pageHeader.isZeroData) {
						indexFileMappedByteBuffer.putInt(tempCell.leftChild+12, curPage.pageHeader.pagePosition);
					}
					
					curPage.writeCell(oldCellNum+i, tempCell);
				}
				
				//写入当前页的最右指针
				tempCell = rightBroPage.readIndexCell(moveNum);
				indexFileMappedByteBuffer.putInt(tempCell.leftChild+12, curPage.pageHeader.pagePosition);
				
				curPage.pageHeader.rightMostP = tempCell.leftChild;
				curPage.pageHeader.writeRightMostP();
				
				for (int i = 1; i <= moveNum; i++) {
					rightBroPage.deleteCell(i);
				}
				
				//修改父亲节点中curPage对应的cell的key值。最后一个tempCell即移入curPage的最大值，可作为该key值。
				curCell.key = tempCell.key;
				parentPage.coverCell(insertIndex, curCell);
				
				return;
			}
			
			//不能移动，即需要考虑合并了
			//内部节点：和左节点合并，将左节点的cell移入当前节点。cellNum+1>=insertIndex>1
			if (curPage.pageHeader.isZeroData && leftBroPage != null) {
				//利用左兄弟节点的最右指针构造一个cell
				byte[] minKey = leftBroCell.key;
				Cell rightMostCell = new Cell();
				rightMostCell.key = minKey;
				rightMostCell.keySize = leftBroCell.keySize;
				rightMostCell.leftChild = leftBroPage.pageHeader.rightMostP;
				
				curPage.writeCell(1, rightMostCell);
				indexFileMappedByteBuffer.putInt(rightMostCell.leftChild+12, curPage.pageHeader.pagePosition);
				
				//将所有cell写入当前节点
				Cell tempCell = null;
				for (int i = leftBroPage.pageHeader.cellNum; i >= 1; i--) {
					tempCell = leftBroPage.readIndexCell(i);
					
					//修改移入cell的leftChild对应页面的父页面
					if (leftBroPage.pageHeader.isZeroData) {
						indexFileMappedByteBuffer.putInt(tempCell.leftChild+12, curPage.pageHeader.pagePosition);
					}
					
					curPage.writeCell(1, tempCell);
				}
				
				
				//修改父亲节点：删除左节点对应Cell。v1 k1 v2  要删除v1 k1，因为v1对应的page的cell都在curPage中了。	
				//保证了父节点只要删除insertIndex对应的cell即可，即保证了上传的信息是cell。
				leftBroCell.setKeyString();
				delete(leftBroCell.keyString, parentPage.pageHeader.pagePosition, insertIndex-1);
				
				//删除左页面
				indexFileAccess.deletePage(leftBroPage);
				
				return;
			}
			
			//内部节点：和右节点合并，将当前节点的cell移入右节点。cellNum+1>insertIndex>=1
			if (curPage.pageHeader.isZeroData && rightBroPage != null) {
				//利用当前节点的最右指针构造一个cell
				Cell curCell = parentPage.readIndexCell(insertIndex);
				Cell rightMostCell = new Cell();
				rightMostCell.key = curCell.key;
				rightMostCell.keySize = curCell.keySize;
				rightMostCell.leftChild = curPage.pageHeader.rightMostP;
				
				rightBroPage.writeCell(curPage.pageHeader.cellNum, rightMostCell);
				indexFileMappedByteBuffer.putInt(rightMostCell.leftChild+12, rightBroPage.pageHeader.pagePosition);
				//将所有cell写入右节点
				Cell tempCell = null;
				for (int i = curPage.pageHeader.cellNum; i >= 1; i--) {
					tempCell = curPage.readIndexCell(i);
					
					//修改移入cell的leftChild对应页面的父页面
					if (curPage.pageHeader.isZeroData) {
						indexFileMappedByteBuffer.putInt(tempCell.leftChild+12, rightBroPage.pageHeader.pagePosition);
					}
					
					rightBroPage.writeCell(1, tempCell);
				}
				
				//修改父亲节点：直接删除curCell。v2 k2 v3，其中v2对应的page的所有内容都在v3对应的页面中
				//保证了父节点只要删除insertIndex对应的cell即可，即保证了上传的信息是cell。
				curCell.setKeyString();
				delete(curCell.keyString, parentPage.pageHeader.pagePosition, insertIndex);
				
				//删除当前页面：已无其他页面指向当前页面。
				indexFileAccess.deletePage(curPage);
				
				return;
			}
		}
	}
}
