package seek;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.MappedByteBuffer;

import javax.swing.JOptionPane;

import basement.Cell;
import basement.IndexFileAccess;
import basement.KeyType;
import basement.Page;

public class Seek {
	public static KeyType dbKeyType = KeyType.INT;
	public static IndexFileAccess indexFileAccess; 
	public static MappedByteBuffer indexFileMappedByteBuffer;
	private static Page rootPage = null;
	
	public static void initSeek(KeyType dbKeyType, IndexFileAccess indexFileAccess, MappedByteBuffer indexFileMappedByteBuffer) {
		Seek.dbKeyType = dbKeyType;
		Seek.indexFileAccess = indexFileAccess;
		Seek.indexFileMappedByteBuffer = indexFileMappedByteBuffer;
	}
	
	public static SeekInfo seek(String key) {
		rootPage = indexFileAccess.readRootPage();
		
		return seek(rootPage, key);
	}
	
	private static boolean isFirst = true;
	private static SeekInfo seek(Page curPage, String key) {
		if (curPage.pageHeader.isZeroData) {
			int insertIndex = curPage.findInsertIndex(key);
			
			if (insertIndex <= curPage.pageHeader.cellNum) {
				Cell tempCell = curPage.readIndexCell(insertIndex);
				return seek(indexFileAccess.readPage(tempCell.leftChild), key);
			} else {
				return seek(indexFileAccess.readPage(curPage.pageHeader.rightMostP), key);
			}
		}
		
		//如果是叶子结点，查找插入点。然后比较插入点前一个cell是否与之相等。
		if (curPage.pageHeader.isLeaf) {
			int insertIndex = curPage.findInsertIndex(key);
			
			PrintWriter out = null;
			try {
				if (isFirst) {
					out = new PrintWriter(".\\db\\seek.txt");
					out.print("");
					out.close();
					isFirst = false;
				}
				
				out = new PrintWriter(new FileWriter(".\\db\\seek.txt", true));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			if (insertIndex == 1) {
				//JOptionPane.showMessageDialog(null, "In seek(Page, String):No such key ["+key+ "] exists");
				return null;
			} else {
				Cell tempCell = curPage.readIndexCell(insertIndex-1);
				
				tempCell.setKeyString();
				if (key.equals(tempCell.keyString)) {
					out.println(tempCell + "\n-----------------is in----------------------\n"+curPage.pageHeader+"\n");
					//System.out.println(new SeekInfo(key, curPage.pageHeader.pagePosition, insertIndex-1));
					out.close();
					return new SeekInfo(key, curPage.pageHeader.pagePosition, insertIndex-1);
				} else {
					//JOptionPane.showMessageDialog(null, "In seek(Page, String):No such key ["+key+ "] exists");
					return null;
				}
			}
		}
		return null;		
	}
	
}
