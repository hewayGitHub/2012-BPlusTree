package basement;
public class StackElement {
	public Page stackPage;
	public int stackIndex;
	
	public StackElement(Page stackPage, int stackIndex) {
		this.stackPage = stackPage;
		this.stackIndex = stackIndex;
	}
	
	public StackElement() {
		
	}
	
	@Override
	public String toString() {
		return "\nstack[page+index"+stackPage
				+"\n"+stackIndex+"\n]\n";
	}
}
