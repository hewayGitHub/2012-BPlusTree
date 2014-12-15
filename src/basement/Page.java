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
 *����ҳ��Ԫ�صĵײ�ӿڡ���ַͳһ��ҳ��ƫ�ơ�
 *ע�⣺
 *1�����п������ҳ��cell content area�еĿ��п顣ÿ�����п��ײ�Ϊ����һ�����п��ҳ��ƫ�� short���ÿ��п�ĳߴ� short����
 *   ���п鰴ҳ��ƫ�������������������ںϲ����ڵĿ��п顣
 *   ���п��С����>4����ΪҪ��������Ķ�Ԫ��
 *2�����ҽڵ㡣Ҷ�ӽڵ�û�����ҽڵ㣬�������ҽڵ�Ϊ0���ڲ��ڵ�����ҽڵ�Ҫע��ά����
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
		public short firstFreeBlockOffset = FIRST_FREEBLOCK_OFFSET;//Ĭ��Ϊ0.ֻ����ָ��cell content area��ɾ���Ŀ���block
		public short cellNum = CELL_NUM;
		public short firstCellOffset = FIRST_CELL_OFFSET;
		public byte fragmentBytes = FRAGMENT_BYTES;
		public int rightMostP = RIGHTMOST_P;
		public int parentPosition = PARENT_POSITION;
		
		public int pagePosition;
		
		//��ֹ�ڰ��ⴴ����ͷ
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
	 * ����ǰҳ���ҳͷд�����
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
	 * ��ȡ��index��cell��index��1��ʼ��		
	 * @param index����1��ʼ
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
	 * ��ȡĳҳ��ƫ�ƴ���cell
	 * @param pageOffset ҳ��ƫ��
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
	 * ����һ��cell������������cell pointer��cell�������޸�ҳͷ��cell num��
	 * @param insertIndex
	 * @param cellOffset
	 * @param inputCell
	 */
	public void writeCell(int insertIndex, int cellOffset, Cell inputCell) {
		//����cell pointer
		insertCellPointer(insertIndex, (short)cellOffset);
		
		//����cell
		IndexFileAccess.indexFileMappedByteBuffer.position(pageHeader.pagePosition+cellOffset);
		
		IndexFileAccess.indexFileMappedByteBuffer.put(inputCell.keySize);;
		IndexFileAccess.indexFileMappedByteBuffer.put(inputCell.key);
		IndexFileAccess.indexFileMappedByteBuffer.putInt(inputCell.leftChild);
		
		//�޸�cellnum
		pageHeader.cellNum++;
		pageHeader.writeCellNum();
	}
	
	/**
	 * ����һ��cell����ǰҳ�С�Ҫ�Լ��жϴ������ɿ黹��δ����ռ䣬���ǲ������Ƿ���Ҫ���ѵ�ǰҳ�档
	 * �����õ㣺��ɾ������ڵ���С����ֵ�Ľڵ������µĽڵ㡣
	 * @param insertIndex
	 * @param inputCell
	 */
	public void writeCell(int insertIndex, Cell inputCell) {
		int freeCellSize = pageHeader.firstCellOffset - Page.PAGEHEADER_SIZE - pageHeader.cellNum*2;
		//��freeCellSizeС��2�ǣ��޷�д��CellPointer�����뱨��
		if (freeCellSize < 2) {
			JOptionPane.showMessageDialog(null, "In writeCell(int, Cell): freeCellSize <2");
			return;
		}
		
		//������п��пռ�ʹ�����п�
		if (saveToFreeBlock(insertIndex, inputCell)) {
			return;
		} else {
			//û�п��õĿ��п飬��page��δ����ռ�Ĵ�СҲ��������Ҫ���ѡ����򣬴���δ����ռ�
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
	 * ɾ����index��cell��
	 * 1��ɾ����Ӧ��cellPointer��ά��cellPointer��
	 * 2�����ո�cell�Ŀռ�
	 * 3���޸�ҳͷ��cellNum
	 * @param index
	 */
	public void deleteCell(int index) {
		short deleteCellPointer = readCellPointer(index);
		Cell deleteCell = readOffsetCell(deleteCellPointer);
		short cellSize = (short) (deleteCell.keySize+Cell.FIXED_SIZE);
		
		/**
		 * ɾ��cell��������ռ�
		 * 1,�����cellλ��δ����ռ�ײ���ֱ���޸�firstCellOffset���ɡ�
		 * 2,������Ϊcell�Ĺ̶��ߴ����4������ֱ�ӽ�cell�����ɿ����
		 */
		if (deleteCellPointer == pageHeader.firstCellOffset) {
			pageHeader.firstCellOffset += cellSize;
			pageHeader.writeFirstCellOffset();
		} else {
			addFreeBlock(deleteCellPointer, cellSize);
		}
		
		//ɾ����Ӧ��cellPointer
		deleteCellPointer(index);
		
		//�޸�ҳͷ��cellNum
		pageHeader.cellNum --;
		pageHeader.writeCellNum();
	}
	
	private void deleteCellPointer(int index) {
		//��Ҫ�����ƶ�����cellpointer
		if (index < pageHeader.cellNum) {
			byte[] moveCellPointer = new byte[(pageHeader.cellNum-index)*2];
			IndexFileAccess.indexFileMappedByteBuffer.position(pageHeader.pagePosition+PAGEHEADER_SIZE+2*index);
			IndexFileAccess.indexFileMappedByteBuffer.get(moveCellPointer);
			
			IndexFileAccess.indexFileMappedByteBuffer.position(pageHeader.pagePosition+PAGEHEADER_SIZE+2*(index-1));
			IndexFileAccess.indexFileMappedByteBuffer.put(moveCellPointer);
		} 
	}
	/**
	 * ��ָ������������cell pointer��������Ϊĳ��cell��ҳ��ƫ�ƣ���cellOffset
	 * @param index ����cellPointer����������1��ʼ��
	 * @param cellOffset����Ӧcell��ҳ��ƫ��
	 */
	private void insertCellPointer(int index, short cellOffset) {
		//��Ҫ�ƶ�����cellpointer
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
	 * ��cellд��δ����ռ䡣
	 * ע�⣺�����ռ��Ƿ��㹻����insert����������ɼ�顣
	 * @param insertIndex ����λ�õ���������Ȼ���Լ��㣬�����ⲿ�б���ʱ��ֱ�ӷ���ȽϺ�
	 * @param inputCell
	 */
	public void saveToUnallocatedSpace(int insertIndex, Cell inputCell) {
		short cellSize = (short) (Cell.FIXED_SIZE+inputCell.keySize);
		
		//��cellд��ָ����ҳ��ƫ�ƣ�writeCell�������Ӧ��cellPointer
		writeCell(insertIndex, pageHeader.firstCellOffset-cellSize, inputCell);
		
		//��cellд��δ����ռ�Ż��޸�firstCellOffset��д����п飬�޸Ŀ��п�����
		pageHeader.firstCellOffset -= cellSize;
		pageHeader.writeFirstCellOffset();
	}
	
	/**
	 * ������ȡĳҳ��ƫ�Ƴ��Ŀ��п飬���ظÿ��п����Ϣ�����ǲ�û�з���ÿ��п顣
	 * @param pageOffset ���п��ҳ��ƫ��
	 * @return �������п���Ϣ��ʵ�����ÿ��п��ҳ��ƫ�ƺ���һ�����п��ҳ��ƫ�ƣ�
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
		 * 1,��ǰ���п��Ϊ��ʱ��д����п飬���޸�ҳͷ��
		 * 2,���򣬶�ȡ���п�ͷ���ҵ���һ��ҳ��ƫ�Ʊ�����Ŀ��п顣�ж��Ƿ���Ժ��ϻ����µĿ�ϲ���
		 * ע�⣺���п�������Ե�ַ�������У����ң����п�˴˵�ַ�����ڡ�
		 */
		if (newPageOffset == 494) {
			int x=1;
		}
		if (pageHeader.firstFreeBlockOffset == 0) {
			pageHeader.firstFreeBlockOffset = newPageOffset;
			pageHeader.writeFirstFreeBlockOffset();
			
			//����Ӧλ��д����п�
			IndexFileAccess.indexFileMappedByteBuffer.position(pageHeader.pagePosition+newPageOffset);
			IndexFileAccess.indexFileMappedByteBuffer.putShort((short) 0);
			IndexFileAccess.indexFileMappedByteBuffer.putShort(newBlockSize);
			
			return;
		} else {
			FreeBlock curBlock = null;
			FreeBlock parentBlock = null;
			//��ȡ���б�ͷ
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
			
			//��Ϊ��������Ϊ�գ�����parentBlock��curBlock������ͬʱΪnull���ҿ��п����parentBlock��curBlock֮��
			if (parentBlock == null) {
				//����������п����ڣ���ϲ����޸Ŀ��п���ͷ����д����п顣
				if (newPageOffset+newBlockSize == curBlock.pageOffset) {
					//�ϲ�
					curBlock.pageOffset = newPageOffset;
					curBlock.bytesNum += newBlockSize;
					
					//�޸Ŀ��б�ͷ
					pageHeader.firstFreeBlockOffset = curBlock.pageOffset;
					pageHeader.writeFirstFreeBlockOffset();
					
					//д����п�
					IndexFileAccess.indexFileMappedByteBuffer.position(pageHeader.pagePosition+curBlock.pageOffset);
					IndexFileAccess.indexFileMappedByteBuffer.putShort(curBlock.nextFreeBlock);
					IndexFileAccess.indexFileMappedByteBuffer.putShort(curBlock.bytesNum);
				} else {
					//�޸Ŀ��б�ͷ
					pageHeader.firstFreeBlockOffset = newPageOffset;
					pageHeader.writeFirstFreeBlockOffset();
					
					//д����п�
					IndexFileAccess.indexFileMappedByteBuffer.position(pageHeader.pagePosition+newPageOffset);
					IndexFileAccess.indexFileMappedByteBuffer.putShort(curBlock.pageOffset);
					IndexFileAccess.indexFileMappedByteBuffer.putShort(newBlockSize);
				}
				return;
			} 
			
			//�����β
			if (curBlock == null) {
				//����������п����ڣ���ϲ����޸Ŀ��п���ͷ����д����п顣
				if (parentBlock.pageOffset+parentBlock.bytesNum == newPageOffset) {
					//�ϲ�
					parentBlock.bytesNum += newBlockSize;
					
					//�ڿ��п��β�����޸Ŀ��п�Ĵ�С
					IndexFileAccess.indexFileMappedByteBuffer.putShort(pageHeader.pagePosition+parentBlock.pageOffset+2, parentBlock.bytesNum);
				} else {
					//�ڿ��б�β�������½ڵ�
					parentBlock.nextFreeBlock = newPageOffset;
					IndexFileAccess.indexFileMappedByteBuffer.putShort(pageHeader.pagePosition+parentBlock.pageOffset, parentBlock.nextFreeBlock);
					
					//д����п�
					IndexFileAccess.indexFileMappedByteBuffer.position(pageHeader.pagePosition+newPageOffset);
					IndexFileAccess.indexFileMappedByteBuffer.putShort((short) 0);
					IndexFileAccess.indexFileMappedByteBuffer.putShort(newBlockSize);
				}
				
				return;
			//�����ܸ�����ϲ���Ҳ���ܸ�����ϲ�
			} else{
				//���ڵ��������ɿ���Ժϲ�Ϊһ����
				if (newPageOffset+newBlockSize == curBlock.pageOffset && parentBlock.pageOffset+parentBlock.bytesNum == newPageOffset) {
					//�ϲ������޸Ŀ��п�ĳߴ�
					parentBlock.bytesNum += newBlockSize+curBlock.bytesNum;
					IndexFileAccess.indexFileMappedByteBuffer.putShort(pageHeader.pagePosition+parentBlock.pageOffset+2, parentBlock.bytesNum);
					
					return;
				}
				
				//ֻ�ܺ�����Ŀ�ϲ���
				if (newPageOffset+newBlockSize == curBlock.pageOffset) {
					//�ϲ�
					curBlock.pageOffset = newPageOffset;
					curBlock.bytesNum += newBlockSize;
					
					//�޸�parentBlockָ�����һ�����п�
					parentBlock.nextFreeBlock = curBlock.pageOffset;
					IndexFileAccess.indexFileMappedByteBuffer.putShort(pageHeader.pagePosition+parentBlock.pageOffset, parentBlock.nextFreeBlock);
					
					//д����п�
					IndexFileAccess.indexFileMappedByteBuffer.position(pageHeader.pagePosition+curBlock.pageOffset);
					IndexFileAccess.indexFileMappedByteBuffer.putShort(curBlock.nextFreeBlock);
					IndexFileAccess.indexFileMappedByteBuffer.putShort(curBlock.bytesNum);
					
					return;
				}
				
				//ֻ�ܺ�����Ŀ�ϲ�
				if (parentBlock.pageOffset+parentBlock.bytesNum == newPageOffset) {
					//�ϲ�
					parentBlock.bytesNum += newBlockSize;
					
					//�޸�parentBlock�Ĵ�С
					IndexFileAccess.indexFileMappedByteBuffer.putShort(pageHeader.pagePosition+parentBlock.pageOffset+2, parentBlock.bytesNum);
				}
				
				//���ܺϲ������������
				//�޸�parentBlock
				parentBlock.nextFreeBlock = newPageOffset;
				IndexFileAccess.indexFileMappedByteBuffer.putShort(pageHeader.pagePosition+parentBlock.pageOffset, parentBlock.nextFreeBlock);
				//д��
				IndexFileAccess.indexFileMappedByteBuffer.position(pageHeader.pagePosition+newPageOffset);
				IndexFileAccess.indexFileMappedByteBuffer.putShort(curBlock.pageOffset);
				IndexFileAccess.indexFileMappedByteBuffer.putShort(newBlockSize);
				return;
			}
		}
		
	}
	
	/**
	 * ��������ʾ��ҳ�����еĿ��п顣����ǿգ�����ѭ����
	 */
	public void showAllFreeBlock() {
		try {
			PrintWriter out = new PrintWriter(".\\db\\allFreeBlcok.txt");
			
			if (pageHeader.firstFreeBlockOffset == 0) {
				out.println("It's empty");
			} else {
				FreeBlock curBlock = null;
				//��ȡ���б�ͷ
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
	 * ���������п顣������ɹ�������true����ά��ҳ�ڵĿ��п��������򣬷���false������û�к��ʵĿ��п���Է��䡣
	 * @param indexFileMBF
	 * @param insertIndex������Ӧ�ñ������cell pointer��������index��1��ʼ��
	 * @param data
	 * @return boolean
	 */
	public boolean saveToFreeBlock(int insertIndex, Cell inputCell) {
		//Ϊ0��ʾû�п��п�
		if (pageHeader.firstFreeBlockOffset == 0) {
			return false;
		}
		
		int dataSize = (short)(Cell.FIXED_SIZE+inputCell.keySize);
		
		FreeBlock tempBlock = readFreeBlock(pageHeader.firstFreeBlockOffset);
		FreeBlock parentBlock = null;
		while (true) {
			if (tempBlock.bytesNum >= dataSize) {
				//��ʣ��ռ���ڵ���4ʱ������ԭ���п顣����ɾ��ԭ���п飬��Ҫ�޸Ŀ��п�������������Ƭ���޸�ҳ����Ƭ���������Ҫ�޸�cellNum��
				if ((tempBlock.bytesNum-dataSize) >= 4) {
					//�޸ĸ����ɿ�ĳߴ磬�����޸�ҳͷ
					tempBlock.bytesNum -= dataSize;
					IndexFileAccess.indexFileMappedByteBuffer.putShort(pageHeader.pagePosition+tempBlock.pageOffset+2, tempBlock.bytesNum);
					
					//��cellд�����Ŀռ�
					writeCell(insertIndex, tempBlock.pageOffset+tempBlock.bytesNum, inputCell);
					
					return true;
				} else {
					//�޸Ŀ�������
					if (parentBlock == null) {
						pageHeader.firstFreeBlockOffset = tempBlock.nextFreeBlock;
						pageHeader.writeFirstFreeBlockOffset();
					} else {
						parentBlock.nextFreeBlock = tempBlock.nextFreeBlock;
						IndexFileAccess.indexFileMappedByteBuffer.putShort(pageHeader.pagePosition+parentBlock.pageOffset, parentBlock.nextFreeBlock);
					}
					
					//�޸���Ƭ��
					pageHeader.fragmentBytes += tempBlock.bytesNum-dataSize;
					pageHeader.writeFragmentBytes();
					
					//��cellд�����Ŀռ�
					writeCell(insertIndex, tempBlock.pageOffset, inputCell);
					return true;
				}
			}
			
			//���һ�����п�Ҳû���㹻�Ŀռ�
			if (tempBlock.nextFreeBlock == 0) {
				return false;
			}
			
			parentBlock = tempBlock;
			tempBlock = readFreeBlock(parentBlock.nextFreeBlock);
		}
	}
	
	/**
	 * ������һ�����ڸ�key��cell�����ظ�cell��λ��������
	 * @param key �������key(String)
	 * @return index����1��ʼ��
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
	 * ��˳����ʾҳ������cell
	 * @param indexFileMBF
	 * @param keyType
	 */
	public void showAllKeyCell() {
		showAllCell(".\\db\\allCell.txt");
	}
	
	public void showAllCell(String fileName) {
		try {
			PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(fileName)));
			
			//���cell����
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
	 * ���ҳ�������е�cell�����������ͺ�parentPostion֮���ֵ����Ϊ��ʼֵ��
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
