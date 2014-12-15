package basement;

import java.math.BigInteger;

import main.DataBase;

/**
 * ע�⣺���û�и�keyString��ֵ����Ĭ����0.��Ҫ�Լ���key��������ת��ΪkeyString
 * @author heway
**   OFFSET   SIZE     DESCRIPTION
**      0       1      keySize n(4:int 8:long double:16 string:any)
**      1       n      key:byte[]
**      n+1     4      leftChild:file offset
 *
 */
public class Cell {
	public static final int FIXED_SIZE = 5;
	
	public byte keySize;
	public byte[] key;
	public int leftChild;
	
	public String keyString = null;
	@Override
	public String toString() {
		
		return "Cell["
		+"\nkey:"+keyString
		+"\nkeySize:"+keySize
		+"\nleftChild:"+leftChild
		+"\n]Cell";
	}
	
	public void setKeyString() {
		switch (DataBase.dbKeyType) {
		case INT:
			keyString = String.valueOf(new BigInteger(key).intValue());
			break;
		case LONG:
			keyString = String.valueOf(new BigInteger(key).longValue());
			break;
		case DOUBLE:
			//keyString = String.valueOf());
			throw new UnsupportedOperationException();
			//break;
		case STRING:
			keyString = new String(key);
			break;
		}
	}
}
