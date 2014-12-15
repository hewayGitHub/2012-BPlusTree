package insert;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

import javax.swing.JOptionPane;

import basement.Cell;
import main.DataBase;

public class SplitInfo {
	public int insertIndex;
	public byte keySize;
	public String insertKey;
	public int pagePosition;
	
	public SplitInfo(int insertIndex, String insertKey, int pagePosition) {
		this.insertIndex = insertIndex;
		this.insertKey = insertKey;
		this.pagePosition = pagePosition;
		
		switch (DataBase.dbKeyType) {
		case INT:
			keySize = 4;
			break;
		case LONG:
			keySize = 8;
			break;
		case DOUBLE:
			keySize = 64;	
			break;
		case STRING:
			keySize = (byte) this.insertKey.length();
			break;
		}
	}
	
	public Cell toCell() {
		try {
			Cell newCell = new Cell();
			newCell.keySize = keySize;
			byte[] keyBytes = new byte[keySize];
			
			ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
	        DataOutputStream dataOut = new DataOutputStream(byteOut);
			switch (DataBase.dbKeyType) {
			case INT:
				 dataOut.writeInt(Integer.parseInt(insertKey));
				 keyBytes = byteOut.toByteArray();
				break;
			case LONG:
				dataOut.writeLong(Long.parseLong(insertKey));
				keyBytes = byteOut.toByteArray();
				break;
			case DOUBLE:
				dataOut.writeDouble(Double.parseDouble(insertKey));
				keyBytes = byteOut.toByteArray();
				break;
			case STRING:
				keyBytes = insertKey.getBytes();
				break;
			}
			newCell.key = keyBytes;
			newCell.leftChild = pagePosition;
			
			byteOut.close();
			dataOut.close();
			return newCell;
		} catch (Exception e) {
			// TODO: handle exception
			JOptionPane.showMessageDialog(null, "In toCell(): keyType error");
			System.exit(-1);
			return null;
		}
	}
	
	@Override
	public String toString() {
		return "\n[SplitInfo]" 
				+"\nkey:"+insertKey
				+"\npagePostion:"+pagePosition
				+"\ninsertIndex:"+insertIndex
				+"\n[/SplitInfo]";
	}
}
