package basement;

import insert.SplitInfo;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.math.BigInteger;
import javax.swing.JOptionPane;

/**
 * 
 * @author heway
 *操作页内元素的底层接口。地址统一用页内偏移。
 *注意：
 *1，空闲块表。管理页内cell content area中的空闲块。每个空闲块首部为（下一个空闲块的页内偏移 short，该空闲块的尺寸 short）。
 *   空闲块按页内偏移升序链接起来，便于合并相邻的空闲块。
 *   空闲块大小必须>4，因为要存放上述的二元组
 *2，最右节点。叶子节点没有最右节点，所以最右节点为0。内部节点的最右节点要注意维护。
**   OFFSET   SIZE     DESCRIPTION
**      0       1      Flags. 1: intkey, 2: zerodata , 4:leaf
**      1       2      byte offset to the first freeblock
**      3       2      number of cells on this page
**      5       2      first byte of the cell content area
**      7       1      number of fragmented free bytes
**      8       4      Right child (the Ptr(N) value).  Omitted on leaves.
**     12       4      parentPosition
 */
public class Page {
	public PageHeader pageHeader = new PageHeader();
	public static final int PAGEHEADER_SIZE = 16;
	public static class PageHeader {
		static final short FIRST_FREEBLOCK_OFFSET = 0;
		static final short CELL_NUM = 0;
		static final short FIRST_CELL_OFFSET = IndexFileAccess.IndexFileHeader.PAGE_SIZE;
		static final byte FRAGMENT_BYTES = 0;
		static final int RIGHTMOST_P = 0;
		static final int PARENT_POSITION = 0;
		
		public boolean isIntKey = false, isZeroData, isLeaf;
		public short firstFreeBlockOffset = FIRST_FREEBLOCK_OFFSET;//默认为0.只用来指向cell content area中删除的空闲block
		public short cellNum = CELL_NUM;
		public short firstCellOffset = FIRST_CELL_OFFSET;
		public byte fragmentBytes = FRAGMENT_BYTES;
		public int rightMostP = RIGHTMOST_P;
		public int parentPosition = PARENT_POSITION;
		
		public int pagePosition;
		
		//禁止在包外创建表头
		PageHeader() {
			super();
		}
		public void writeFirstFreeBlockOffset() {
			IndexFileAccess.indexFileMappedByteBuffer.putShort(pagePosition+1, firstFreeBlockOffset);
		}
		
		public void writeCellNum() {
			IndexFileAccess.indexFileMappedByteBuffer.putShort(pagePosition+3, cellNum);
		}
		
		public void writeFirstCellOffset() {
			IndexFileAccess.indexFileMappedByteBuffer.putShort(pagePosition+5, firstCellOffset);
		}
		
		public void writeFragmentBytes() {
			IndexFileAccess.indexFileMappedByteBuffer.put(pagePosition+7, fragmentBytes);
		}
		
		public void writeRightMostP() {
			IndexFileAccess.indexFileMappedByteBuffer.putInt(pagePosition+8, rightMostP);
		}
		
		public void writeParentPosition() {
			IndexFileAccess.indexFileMappedByteBuffer.putInt(pagePosition+12, parentPosition);
		}
		
		public FreeBlock readFirstFreeBlock() {
			if (firstFreeBlockOffset == 0) {
				return null;
			} else {
				FreeBlock tempBlock = new FreeBlock();
				tempBlock.pageOffset = firstFreeBlockOffset;
				IndexFileAccess.indexFileMappedByteBuffer.position(pagePosition+firstFreeBlockOffset);
				tempBlock.nextFreeBlock = IndexFileAccess.indexFileMappedByteBuffer.getShort();
				tempBlock.bytesNum  = IndexFileAccess.indexFileMappedByteBuffer.getShort();
				return tempBlock;
			}
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder("Page[");
			sb.append("\nposition:"+pagePosition);
			sb.append("\nisIntKey:"+isIntKey);
			sb.append("\nisZeroData:"+isZeroData);
			sb.append("\nisLeaf:"+isLeaf);
			sb.append("\nfirstFreeBlockOffset:"+firstFreeBlockOffset);
			sb.append("\ncellNum:"+cellNum);
			sb.append("\nfirstCellOffset:"+firstCellOffset);
			sb.append("\nfragmentBytes:"+fragmentBytes);
			sb.append("\nrightMostP:"+rightMostP);
			sb.append("\nparentPostion:"+parentPosition);
			sb.append("\n]Page");
			return sb.toString();
		}
	}
	
	/**
	 * 将当前页面的页头写入外存
	 */
	public void writePageHeader(){
		byte flag = 0x00;
		if (pageHeader.isIntKey) {
			flag |= 0x01;
		}
		if (pageHeader.isZeroData) {
			flag |= 0x02;
		}
		if (pageHeader.isLeaf) {
			flag |= 0x04;
		}
		
		IndexFileAccess.indexFileMappedByteBuffer.position(pageHeader.pagePosition);
		
		IndexFileAccess.indexFileMappedByteBuffer.put(flag);
		IndexFileAccess.indexFileMappedByteBuffer.putShort(pageHeader.firstFreeBlockOffset);
		IndexFileAccess.indexFileMappedByteBuffer.putShort(pageHeader.cellNum);
		IndexFileAccess.indexFileMappedByteBuffer.putShort(pageHeader.firstCellOffset);
		IndexFileAccess.indexFileMappedByteBuffer.put(pageHeader.fragmentBytes);
		IndexFileAccess.indexFileMappedByteBuffer.putInt(pageHeader.rightMostP);
		IndexFileAccess.indexFileMappedByteBuffer.putInt(pageHeader.parentPosition);
	}
	
	/**
	 * 读取第index个cell，index从1开始。		
	 * @param index：从1开始
	 * @return Cell
	 */
	public Cell readIndexCell(int index) {
		if (index > pageHeader.cellNum) {
			JOptionPane.showMessageDialog(null, "In readCell(index):index>cellNum");
			return null;
		}
		
		short cellPointer;
		cellPointer = IndexFileAccess.indexFileMappedByteBuffer.getShort(pageHeader.pagePosition+PAGEHEADER_SIZE+(index-1)*2);
		
		return readOffsetCell(cellPointer);
	}
	
	/**
	 * 读取某页内偏移处的cell
	 * @param pageOffset 页内偏移
	 * @return Cell
	 */
	public Cell readOffsetCell(short pageOffset) {
		Cell cell = new Cell();
		
		IndexFileAccess.indexFileMappedByteBuffer.position(pageHeader.pagePosition+pageOffset);
		
		cell.keySize = IndexFileAccess.indexFileMappedByteBuffer.get();
		
		byte[] keyBytes = new byte[cell.keySize];
		IndexFileAccess.indexFileMappedByteBuffer.get(keyBytes);
		cell.key = keyBytes;
		
		cell.leftChild = IndexFileAccess.indexFileMappedByteBuffer.getInt();
		
		return cell;
	}
	
	/**
	 * 插入一个cell。包括，插入cell pointer和cell本身，并修改页头的cell num。
	 * @param insertIndex
	 * @param cellOffset
	 * @param inputCell
	 */
	public void writeCell(int insertIndex, int cellOffset, Cell inputCell) {
		//插入cell pointer
		insertCellPointer(insertIndex, (short)cellOffset);
		
		//插入cell
		IndexFileAccess.indexFileMappedByteBuffer.position(pageHeader.pagePosition+cellOffset);
		
		IndexFileAccess.indexFileMappedByteBuffer.put(inputCell.keySize);;
		IndexFileAccess.indexFileMappedByteBuffer.put(inputCell.key);
		IndexFileAccess.indexFileMappedByteBuffer.putInt(inputCell.leftChild);
		
		//修改cellnum
		pageHeader.cellNum++;
		pageHeader.writeCellNum();
	}
	
	/**
	 * 插入一个cell到当前页中。要自己判断存入自由块还是未分配空间，但是不考虑是否需要分裂当前页面。
	 * 被调用点：在删除中向节点数小于阈值的节点添入新的节点。
	 * @param insertIndex
	 * @param inputCell
	 */
	public void writeCell(int insertIndex, Cell inputCell) {
		int freeCellSize = pageHeader.firstCellOffset - Page.PAGEHEADER_SIZE - pageHeader.cellNum*2;
		//当freeCellSize小于2是，无法写入CellPointer，必须报错。
		if (freeCellSize < 2) {
			JOptionPane.showMessageDialog(null, "In writeCell(int, Cell): freeCellSize <2");
			return;
		}
		
		//如果空闲块有空间就存入空闲块
		if (saveToFreeBlock(insertIndex, inputCell)) {
			return;
		} else {
			//没有可用的空闲块，且page内未分配空间的大小也不够，需要分裂。否则，存入未分配空间
			if (freeCellSize<(Cell.FIXED_SIZE+inputCell.keySize+2)) {
				JOptionPane.showMessageDialog(null, "In writeCell(int, Cell): freeCellSize <datasize+2");
				return;
			} else {
				saveToUnallocatedSpace(insertIndex, inputCell);
				return;
			}
		}
	}
	
	public short readCellPointer(int index) {
		return IndexFileAccess.indexFileMappedByteBuffer.getShort(pageHeader.pagePosition+PAGEHEADER_SIZE+2*(index-1));
	}
	
	public void coverCell(int index, Cell inputCell) {
	    int cellOffset = readCellPointer(index);
		IndexFileAccess.indexFileMappedByteBuffer.position(pageHeader.pagePosition+cellOffset);
		
		IndexFileAccess.indexFileMappedByteBuffer.put(inputCell.keySize);;
		IndexFileAccess.indexFileMappedByteBuffer.put(inputCell.key);
		IndexFileAccess.indexFileMappedByteBuffer.putInt(inputCell.leftChild);
	}
	
	/**
	 * 删除第index个cell。
	 * 1，删除对应的cellPointer，维护cellPointer。
	 * 2，回收该cell的空间
	 * 3，修改页头的cellNum
	 * @param index
	 */
	public void deleteCell(int index) {
		short deleteCellPointer = readCellPointer(index);
		Cell deleteCell = readOffsetCell(deleteCellPointer);
		short cellSize = (short) (deleteCell.keySize+Cell.FIXED_SIZE);
		
		/**
		 * 删除cell，回收其空间
		 * 1,如果该cell位于未分配空间底部，直接修改firstCellOffset即可。
		 * 2,否则，因为cell的固定尺寸大于4，所以直接将cell当自由块回收
		 */
		if (deleteCellPointer == pageHeader.firstCellOffset) {
			pageHeader.firstCellOffset += cellSize;
			pageHeader.writeFirstCellOffset();
		} else {
			addFreeBlock(deleteCellPointer, cellSize);
		}
		
		//删除对应的cellPointer
		deleteCellPointer(index);
		
		//修改页头的cellNum
		pageHeader.cellNum --;
		pageHeader.writeCellNum();
	}
	
	private void deleteCellPointer(int index) {
		//需要往上移动其他cellpointer
		if (index < pageHeader.cellNum) {
			byte[] moveCellPointer = new byte[(pageHeader.cellNum-index)*2];
			IndexFileAccess.indexFileMappedByteBuffer.position(pageHeader.pagePosition+PAGEHEADER_SIZE+2*index);
			IndexFileAccess.indexFileMappedByteBuffer.get(moveCellPointer);
			
			IndexFileAccess.indexFileMappedByteBuffer.position(pageHeader.pagePosition+PAGEHEADER_SIZE+2*(index-1));
			IndexFileAccess.indexFileMappedByteBuffer.put(moveCellPointer);
		} 
	}
	/**
	 * 在指定索引处插入cell pointer，其内容为某个cell的页内偏移，即cellOffset
	 * @param index 插入cellPointer的索引，从1开始。
	 * @param cellOffset：对应cell的页内偏移
	 */
	private void insertCellPointer(int index, short cellOffset) {
		//需要移动其他cellpointer
		if (index <= pageHeader.cellNum) {
			byte[] moveCellPointer = new byte[(pageHeader.cellNum-index+1)*2];
			IndexFileAccess.indexFileMappedByteBuffer.position(pageHeader.pagePosition+PAGEHEADER_SIZE+2*(index-1));
			IndexFileAccess.indexFileMappedByteBuffer.get(moveCellPointer);
			
			IndexFileAccess.indexFileMappedByteBuffer.position(pageHeader.pagePosition+PAGEHEADER_SIZE+2*(index-1));
			IndexFileAccess.indexFileMappedByteBuffer.putShort(cellOffset);
			IndexFileAccess.indexFileMappedByteBuffer.put(moveCellPointer);
		} else {
			IndexFileAccess.indexFileMappedByteBuffer.position(pageHeader.pagePosition+PAGEHEADER_SIZE+2*(index-1));
			IndexFileAccess.indexFileMappedByteBuffer.putShort(cellOffset);
		}
	}
	
	/**
	 * 将cell写入未分配空间。
	 * 注意：不检查空间是否足够，由insert函数自身完成检查。
	 * @param insertIndex 插入位置的索引，虽然可以计算，但是外部有保存时，直接放入比较好
	 * @param inputCell
	 */
	public void saveToUnallocatedSpace(int insertIndex, Cell inputCell) {
		short cellSize = (short) (Cell.FIXED_SIZE+inputCell.keySize);
		
		//将cell写入指定的页内偏移，writeCell会添加相应的cellPointer
		writeCell(insertIndex, pageHeader.firstCellOffset-cellSize, inputCell);
		
		//将cell写入未分配空间才会修改firstCellOffset。写入空闲块，修改空闲块链表。
		pageHeader.firstCellOffset -= cellSize;
		pageHeader.writeFirstCellOffset();
	}
	
	/**
	 * 单纯读取某页内偏移出的空闲块，返回该空闲块的信息，但是并没有分配该空闲块。
	 * @param pageOffset 空闲块的页内偏移
	 * @return 包含空闲块信息的实例（该空闲块的页内偏移和下一个空闲块的页内偏移）
	 */
	public  FreeBlock readFreeBlock(short pageOffset) {
		FreeBlock freeBlock = new FreeBlock();
		IndexFileAccess.indexFileMappedByteBuffer.position(pageHeader.pagePosition+pageOffset);
		
		freeBlock.pageOffset = pageOffset;
		freeBlock.nextFreeBlock = IndexFileAccess.indexFileMappedByteBuffer.getShort();
		freeBlock.bytesNum = IndexFileAccess.indexFileMappedByteBuffer.getShort();
		return freeBlock;
	}
	
	private void addFreeBlock(short newPageOffset, short newBlockSize) {
		// TODO Auto-generated method stub
		/**
		 * 1,当前空闲块表为空时，写入空闲块，并修改页头。
		 * 2,否则，读取空闲块头，找到第一个页内偏移比它大的空闲块。判断是否可以和上或者下的块合并。
		 * 注意：空闲块表总是以地址升序排列，而且，空闲块彼此地址不相邻。
		 */
		if (newPageOffset == 494) {
			int x=1;
		}
		if (pageHeader.firstFreeBlockOffset == 0) {
			pageHeader.firstFreeBlockOffset = newPageOffset;
			pageHeader.writeFirstFreeBlockOffset();
			
			//在相应位置写入空闲块
			IndexFileAccess.indexFileMappedByteBuffer.position(pageHeader.pagePosition+newPageOffset);
			IndexFileAccess.indexFileMappedByteBuffer.putShort((short) 0);
			IndexFileAccess.indexFileMappedByteBuffer.putShort(newBlockSize);
			
			return;
		} else {
			FreeBlock curBlock = null;
			FreeBlock parentBlock = null;
			//读取空闲表头
			curBlock = pageHeader.readFirstFreeBlock();
			
			while (true) {
				if (curBlock.pageOffset > newPageOffset) {
					break;
				}
				
				parentBlock = curBlock;
				if (curBlock.nextFreeBlock != 0) {
					curBlock = readFreeBlock(curBlock.nextFreeBlock);
				} else {
					curBlock = null;
					break;
				}
			}
			
			//因为空闲链表不为空，所以parentBlock和curBlock不可能同时为null。且空闲块插入parentBlock和curBlock之间
			if (parentBlock == null) {
				//如果两个空闲块相邻，则合并。修改空闲块表表头，并写入空闲块。
				if (newPageOffset+newBlockSize == curBlock.pageOffset) {
					//合并
					curBlock.pageOffset = newPageOffset;
					curBlock.bytesNum += newBlockSize;
					
					//修改空闲表头
					pageHeader.firstFreeBlockOffset = curBlock.pageOffset;
					pageHeader.writeFirstFreeBlockOffset();
					
					//写入空闲块
					IndexFileAccess.indexFileMappedByteBuffer.position(pageHeader.pagePosition+curBlock.pageOffset);
					IndexFileAccess.indexFileMappedByteBuffer.putShort(curBlock.nextFreeBlock);
					IndexFileAccess.indexFileMappedByteBuffer.putShort(curBlock.bytesNum);
				} else {
					//修改空闲表头
					pageHeader.firstFreeBlockOffset = newPageOffset;
					pageHeader.writeFirstFreeBlockOffset();
					
					//写入空闲块
					IndexFileAccess.indexFileMappedByteBuffer.position(pageHeader.pagePosition+newPageOffset);
					IndexFileAccess.indexFileMappedByteBuffer.putShort(curBlock.pageOffset);
					IndexFileAccess.indexFileMappedByteBuffer.putShort(newBlockSize);
				}
				return;
			} 
			
			//添入表尾
			if (curBlock == null) {
				//如果两个空闲块相邻，则合并。修改空闲块表表头，并写入空闲块。
				if (parentBlock.pageOffset+parentBlock.bytesNum == newPageOffset) {
					//合并
					parentBlock.bytesNum += newBlockSize;
					
					//在空闲块表尾部，修改空闲块的大小
					IndexFileAccess.indexFileMappedByteBuffer.putShort(pageHeader.pagePosition+parentBlock.pageOffset+2, parentBlock.bytesNum);
				} else {
					//在空闲表尾部添入新节点
					parentBlock.nextFreeBlock = newPageOffset;
					IndexFileAccess.indexFileMappedByteBuffer.putShort(pageHeader.pagePosition+parentBlock.pageOffset, parentBlock.nextFreeBlock);
					
					//写入空闲块
					IndexFileAccess.indexFileMappedByteBuffer.position(pageHeader.pagePosition+newPageOffset);
					IndexFileAccess.indexFileMappedByteBuffer.putShort((short) 0);
					IndexFileAccess.indexFileMappedByteBuffer.putShort(newBlockSize);
				}
				
				return;
			//即可能跟上面合并，也可能跟下面合并
			} else{
				//相邻的三个自由块可以合并为一个块
				if (newPageOffset+newBlockSize == curBlock.pageOffset && parentBlock.pageOffset+parentBlock.bytesNum == newPageOffset) {
					//合并，并修改空闲块的尺寸
					parentBlock.bytesNum += newBlockSize+curBlock.bytesNum;
					IndexFileAccess.indexFileMappedByteBuffer.putShort(pageHeader.pagePosition+parentBlock.pageOffset+2, parentBlock.bytesNum);
					
					return;
				}
				
				//只能和下面的块合并。
				if (newPageOffset+newBlockSize == curBlock.pageOffset) {
					//合并
					curBlock.pageOffset = newPageOffset;
					curBlock.bytesNum += newBlockSize;
					
					//修改parentBlock指向的下一个空闲块
					parentBlock.nextFreeBlock = curBlock.pageOffset;
					IndexFileAccess.indexFileMappedByteBuffer.putShort(pageHeader.pagePosition+parentBlock.pageOffset, parentBlock.nextFreeBlock);
					
					//写入空闲块
					IndexFileAccess.indexFileMappedByteBuffer.position(pageHeader.pagePosition+curBlock.pageOffset);
					IndexFileAccess.indexFileMappedByteBuffer.putShort(curBlock.nextFreeBlock);
					IndexFileAccess.indexFileMappedByteBuffer.putShort(curBlock.bytesNum);
					
					return;
				}
				
				//只能和上面的块合并
				if (parentBlock.pageOffset+parentBlock.bytesNum == newPageOffset) {
					//合并
					parentBlock.bytesNum += newBlockSize;
					
					//修改parentBlock的大小
					IndexFileAccess.indexFileMappedByteBuffer.putShort(pageHeader.pagePosition+parentBlock.pageOffset+2, parentBlock.bytesNum);
				}
				
				//不能合并，则插入其中
				//修改parentBlock
				parentBlock.nextFreeBlock = newPageOffset;
				IndexFileAccess.indexFileMappedByteBuffer.putShort(pageHeader.pagePosition+parentBlock.pageOffset, parentBlock.nextFreeBlock);
				//写入
				IndexFileAccess.indexFileMappedByteBuffer.position(pageHeader.pagePosition+newPageOffset);
				IndexFileAccess.indexFileMappedByteBuffer.putShort(curBlock.pageOffset);
				IndexFileAccess.indexFileMappedByteBuffer.putShort(newBlockSize);
				return;
			}
		}
		
	}
	
	/**
	 * 按升序显示该页内所有的空闲块。如果非空，进入循环。
	 */
	public void showAllFreeBlock() {
		try {
			PrintWriter out = new PrintWriter(".\\db\\allFreeBlcok.txt");
			
			if (pageHeader.firstFreeBlockOffset == 0) {
				out.println("It's empty");
			} else {
				FreeBlock curBlock = null;
				//读取空闲表头
				curBlock = pageHeader.readFirstFreeBlock();
				//int i=0;
				while (true) {
/*					if (i++ == 20) {
						break;
					}*/
					out.println(curBlock);
					if (curBlock.nextFreeBlock == 0) {
						break;
					}
					curBlock = readFreeBlock(curBlock.nextFreeBlock);
				}
			}
			out.close();
		} catch (Exception e) {
			// TODO: handle exception
		}
	}
	
	/**
	 * 申请分配空闲块。若分配成功，返回true，并维护页内的空闲块链表。否则，返回false，表明没有合适的空闲块可以分配。
	 * @param indexFileMBF
	 * @param insertIndex：数据应该被插入的cell pointer的索引。index从1开始。
	 * @param data
	 * @return boolean
	 */
	public boolean saveToFreeBlock(int insertIndex, Cell inputCell) {
		//为0表示没有空闲块
		if (pageHeader.firstFreeBlockOffset == 0) {
			return false;
		}
		
		int dataSize = (short)(Cell.FIXED_SIZE+inputCell.keySize);
		
		FreeBlock tempBlock = readFreeBlock(pageHeader.firstFreeBlockOffset);
		FreeBlock parentBlock = null;
		while (true) {
			if (tempBlock.bytesNum >= dataSize) {
				//当剩余空间大于等于4时，保留原空闲块。否则，删除原空闲块，需要修改空闲块链，并产生碎片，修改页内碎片数量。最后都要修改cellNum。
				if ((tempBlock.bytesNum-dataSize) >= 4) {
					//修改该自由块的尺寸，不用修改页头
					tempBlock.bytesNum -= dataSize;
					IndexFileAccess.indexFileMappedByteBuffer.putShort(pageHeader.pagePosition+tempBlock.pageOffset+2, tempBlock.bytesNum);
					
					//将cell写入分配的空间
					writeCell(insertIndex, tempBlock.pageOffset+tempBlock.bytesNum, inputCell);
					
					return true;
				} else {
					//修改空闲链表
					if (parentBlock == null) {
						pageHeader.firstFreeBlockOffset = tempBlock.nextFreeBlock;
						pageHeader.writeFirstFreeBlockOffset();
					} else {
						parentBlock.nextFreeBlock = tempBlock.nextFreeBlock;
						IndexFileAccess.indexFileMappedByteBuffer.putShort(pageHeader.pagePosition+parentBlock.pageOffset, parentBlock.nextFreeBlock);
					}
					
					//修改碎片数
					pageHeader.fragmentBytes += tempBlock.bytesNum-dataSize;
					pageHeader.writeFragmentBytes();
					
					//将cell写入分配的空间
					writeCell(insertIndex, tempBlock.pageOffset, inputCell);
					return true;
				}
			}
			
			//最后一个空闲块也没有足够的空间
			if (tempBlock.nextFreeBlock == 0) {
				return false;
			}
			
			parentBlock = tempBlock;
			tempBlock = readFreeBlock(parentBlock.nextFreeBlock);
		}
	}
	
	/**
	 * 搜索第一个大于该key的cell，返回该cell的位置索引。
	 * @param key 带插入的key(String)
	 * @return index，从1开始。
	 */
	public int findInsertIndex(String key) {
		short cellPointer;
		Cell tempCell = null;
		String keyString = null;
		int i = 0;
		
		boolean intR, longR, doubleR, stringR;
		intR = longR = doubleR = stringR = false;
		for (i = 0; i <  pageHeader.cellNum; i++) {
			cellPointer = IndexFileAccess.indexFileMappedByteBuffer.getShort(pageHeader.pagePosition+Page.PAGEHEADER_SIZE+i*2);
			tempCell = readOffsetCell(cellPointer);
			
			switch (main.DataBase.dbKeyType) {
			case INT:
				if (Integer.parseInt(key) < new BigInteger(tempCell.key).intValue()) {
					intR = true;
				}
				break;
			case LONG:
				if (Long.parseLong(key) < new BigInteger(tempCell.key).longValue()) {
					longR = true;
				}
				break;
			case DOUBLE:
				if (Double.parseDouble(key) < new BigInteger(tempCell.key).doubleValue()) {
					doubleR = true;
				}
				break;
				//throw new UnsupportedOperationException();
				//break;
			case STRING:
				keyString = new String(tempCell.key);
				if (key.compareTo(keyString) < 0) {
					stringR = true;
				}
				break;
			}
			
			if (intR || longR || doubleR || stringR) {
				break;
			}
		}
		
		return i+1;
	}
	
	/**
	 * 按顺序显示页内所有cell
	 * @param indexFileMBF
	 * @param keyType
	 */
	public void showAllKeyCell() {
		showAllCell(".\\db\\allCell.txt");
	}
	
	public void showAllCell(String fileName) {
		try {
			PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(fileName)));
			
			//输出cell总数
			out.println("cellNum:"+pageHeader.cellNum);
			out.println();
			
			short cellPointer;
			Cell tempCell = null;
			for (int i = 0; i < pageHeader.cellNum; i++) {
				IndexFileAccess.indexFileMappedByteBuffer.position(pageHeader.pagePosition+Page.PAGEHEADER_SIZE+i*2);
				cellPointer = IndexFileAccess.indexFileMappedByteBuffer.getShort();
				
				tempCell = readOffsetCell(cellPointer);
				
				tempCell.setKeyString();
				out.println(tempCell+"\n");
			}
			
			out.println("rightMostP:"+pageHeader.rightMostP);
			
			out.close();
		} catch (Exception e) {
			// TODO: handle exception
			JOptionPane.showMessageDialog(null, "In showAllKeyCell: cannot creat file allCell.txt");
		}
	}
	
	/**
	 * 清空页面中所有的cell，即将除类型和parentPostion之外的值都赋为初始值。
	 */
	public void clearAllCell() {
		pageHeader.firstFreeBlockOffset = PageHeader.FIRST_FREEBLOCK_OFFSET;
		pageHeader.cellNum = PageHeader.CELL_NUM;
		pageHeader.firstCellOffset = PageHeader.FIRST_CELL_OFFSET;
		pageHeader.fragmentBytes = PageHeader.FRAGMENT_BYTES;
		pageHeader.rightMostP = PageHeader.RIGHTMOST_P;
		//pageHeader.parentPosition = PageHeader.PARENT_POSITION;
		
		pageHeader.writeFirstFreeBlockOffset();
		pageHeader.writeCellNum();
		pageHeader.writeFirstCellOffset();
		pageHeader.writeFragmentBytes();
		pageHeader.writeRightMostP();
	}
	
	/**
	 * ---|3|
   |---|1|2|3|
   |---|4|5|6|
	 */
	@Override
	public String toString() {
		StringBuilder out = new StringBuilder();
		if (pageHeader.parentPosition == 0) {
			out.append("---|");
		} else {
			out.append("   |---|");
		}
		
		Cell tempCell = null;
		for (int i = 1; i <= pageHeader.cellNum; i++) {
			tempCell = readIndexCell(i);
			tempCell.setKeyString();
			out.append(tempCell.keyString+"|");
		}
		
		return out.toString();
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
