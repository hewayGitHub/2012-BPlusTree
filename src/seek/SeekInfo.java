package seek;

import basement.Page;

/**
 * ���ĳһ��key��B+���е�λ�á�
 * 1����ΪwherePage���ΪPage������ͷ�ļ��п��ܷ����仯��������Ϣ����ġ�������pagePositon
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
