package basement;

public class FreeBlock {
	public short pageOffset; //ҳ�ڵ�ƫ��
	
	public short nextFreeBlock;//��һ�����п��ҳ��ƫ�ƣ�0��ʾû����һ�����п�
	public short bytesNum;
	
	@Override
	public String toString() {
		return "\n[freeeblock]"
				+"\npageoffset:"+pageOffset
				+"\nnextFreeBlock:"+nextFreeBlock
				+"\nbytesnum:"+bytesNum
				+"\n[/freeblock]";
	}
}
