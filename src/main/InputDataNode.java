package main;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import javax.swing.JOptionPane;

import basement.Cell;
/**
 * @author heway
 *
 */
public class InputDataNode {
	public byte keySize;
	public String key;
	public int dataPosition = 0;
	
	public InputDataNode(int dataPosition, String key) {
		this.dataPosition = dataPosition;
		this.key = key;
		
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
			keySize = (byte)key.length();
			break;
		}
	}
	
	public Cell toCell(){
		try {
			Cell newCell = new Cell();
			newCell.keySize = keySize;
			byte[] keyBytes = new byte[keySize];
			
			ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
	        DataOutputStream dataOut = new DataOutputStream(byteOut);
			switch (DataBase.dbKeyType) {
			case INT:
				 dataOut.writeInt(Integer.parseInt(key));
				 keyBytes = byteOut.toByteArray();
				break;
			case LONG:
				dataOut.writeLong(Long.parseLong(key));
				keyBytes = byteOut.toByteArray();
				break;
			case DOUBLE:
				dataOut.writeDouble(Double.parseDouble(key));
				keyBytes = byteOut.toByteArray();
				break;
			case STRING:
				keyBytes = key.getBytes();
				break;
			}
			newCell.key = keyBytes;
			newCell.leftChild = dataPosition;
			
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
	
	public static void main(String... args) {
		//测试toCell函数是否工作正常
		InputDataNode main = new InputDataNode(0, "256");
		Cell newCell = main.toCell();
		newCell.setKeyString();
		System.out.println(newCell);
	}
}