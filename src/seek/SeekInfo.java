package seek;

import basement.Page;

/**
 * 标记某一个key在B+树中的位置。
 * 1，因为wherePage如果为Page，它的头文件有可能发生变化，即该信息是脏的。所以用pagePositon
 * @author heway
 *
 */
public class SeekInfo {
	public String key;
	public int wherePage;
	public int whereIndex;
	
	public SeekInfo(String key, int wherePage, int whereIndex) {
		this.key = key;
		this.whereIndex = whereIndex;
		this.wherePage = wherePage;
	}
	
	@Override
	public String toString() {
		return "\n[seekInfo]"
				+"\nkey:"+key
				+"\nwherePage:"+wherePage
				+"\nwhereIndex:"+whereIndex
				+"\n[/seekInfo]";
	}
}
