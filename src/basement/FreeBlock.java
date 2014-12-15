package basement;

public class FreeBlock {
	public short pageOffset; //页内的偏移
	
	public short nextFreeBlock;//下一个空闲块的页内偏移，0表示没有下一个空闲块
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
