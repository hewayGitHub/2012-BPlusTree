package basement;
import java.util.LinkedList;

/**
 * 存储b+树查找时的路径，最多存10个节点。
 * 其中，index从1开始，最大为cellNum+1，表示最右节点。
 */

/**
 * @author heway
 *
 */
public class PathStack {
	public static final int maxDepth = 10;
	
	
	LinkedList<Page> pageList = new LinkedList<Page>();
	LinkedList<Integer> indexList = new LinkedList<Integer>();
	
	public void push(StackElement sE) {
		if (pageList.size() >= maxDepth) {
			pageList.removeLast();
			indexList.removeLast();
		}
		pageList.addFirst(sE.stackPage);
		indexList.addFirst(sE.stackIndex);
	}
	
	public StackElement pop() {
		StackElement sE = new StackElement();
		sE.stackPage = pageList.removeFirst();
		sE.stackIndex = indexList.removeFirst();
		return sE;
	}
	
	public StackElement peek() {
		StackElement sE = new StackElement();
		sE.stackPage = pageList.getFirst();
		sE.stackIndex = indexList.getFirst();
		return sE;
	}
	
	public boolean isEmpty() {
		return pageList.isEmpty();
	}
	
	public void clear() {
		pageList.clear();
		indexList.clear();
	}
	
	@Override
	public String toString() {
		return pageList.toString();
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
