package main;

import insert.Insert;
import insert.SplitInfo;
import seek.Seek;
import seek.SeekInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.MappedByteBuffer;

import delete.Delete;
import basement.IndexFileAccess;
import basement.KeyType;
import basement.Page;

/**
 * 
 * @author heway
 *
 */
public class DataBase {
	public static KeyType dbKeyType = KeyType.INT;
	public static IndexFileAccess indexFileAccess; 
	public static MappedByteBuffer indexFileMappedByteBuffer;
	public static Page rootPage = null;
	
	/**
	 * 打开索引文件，初始化Insert
	 * @param dbkKeyType
	 */
	public void open(KeyType dbKeyType, int maxCellNum) {
		DataBase.dbKeyType = dbKeyType;
		
		indexFileAccess = new IndexFileAccess();
		rootPage = indexFileAccess.open();
		indexFileMappedByteBuffer = indexFileAccess.indexFileMappedByteBuffer;
		
		Insert.initInsert(dbKeyType, indexFileAccess, indexFileMappedByteBuffer);
		Seek.initSeek(dbKeyType, indexFileAccess, indexFileMappedByteBuffer);
		Delete.initDelete(dbKeyType, indexFileAccess, indexFileMappedByteBuffer);
		
		Delete.MAX_CELLNUM = Insert.MAX_CELLNUM = maxCellNum;
	}
	
	public boolean insert(InputDataNode data) {
		return Insert.insert(data);
	}
	
	public SeekInfo seek(String key) {
		return Seek.seek(key);
	}
	
	public boolean delete(String key) {
		return Delete.delete(key);
	}
	
	public static DataBase dbInstance = new DataBase();
	public boolean isFirst = true;
	public void run(String fileName) {
		try {
			BufferedReader in = new BufferedReader(new FileReader(new File(".//in//"+fileName+".in")));
			PrintWriter out = new PrintWriter(new FileWriter(".//out//"+fileName+".out", true));
			
			if (isFirst) {
				out.print("");
				out.close();
				isFirst = false;
			}
			out = new PrintWriter(new FileWriter(".//out//"+fileName+".out", true));
			
			String line = in.readLine();
			//读入每个Page最大的cell个数，并打开数据库
			dbInstance.open(KeyType.INT, Integer.parseInt(line));

			String[] sentence = null;
			while((line = in.readLine()) != null) {
				sentence = line.split(" ");
				
				if (sentence.length == 1) {
					if (sentence[0].equals("print")) {
						out.close();
						indexFileAccess.printBPlusTree(".//out//"+fileName+".out");
						out = new PrintWriter(new FileWriter(".//out//"+fileName+".out", true));
						continue;
					} else {
						//out.println("Unsupported input format");
					}
				}
				
				if (sentence[0].equals("insert")) {
					if (dbInstance.insert(new InputDataNode(0, sentence[1]))) {
						out.println("insert "+sentence[1]+" ok");
					} else {
						out.println("insert "+sentence[1]+" failed");
					}
					continue;
				}
				
				if (sentence[0].equals("delete")) {
					if (dbInstance.delete(sentence[1])) {
						out.println("delete "+sentence[1]+" ok");
					} else {
						out.println("delete "+sentence[1]+" failed");
					}
					continue;
				}
				
				if (sentence[0].equals("seek")) {
					if (dbInstance.seek(sentence[1]) != null) {
						out.println("seek "+sentence[1]+" ok");
					} else {
						out.println("seek "+sentence[1]+" failed");
					}
					continue;
				}
			}
			
			in.close();
			out.close();
		} catch (Exception e) {
			// TODO: handle exception
		}
		
		
	}
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub
		DataBase main = new DataBase();
		main.open(KeyType.INT, 30);
		
		//测试插入单个数据节点是否正确
		/*InputDataNode data = new InputDataNode(0, "255");
		main.insert(data);
		rootPage = indexFileAccess.readRootPage();
		System.out.println(rootPage.pageHeader);
		rootPage.showAllKeyCell();*/
		
		//测试插入但不分裂。45是临界值
		int num = 45;

		
		//测试根节点的分裂，数据插入是否正确
		num = 46;
		
		//测试当根节点不是叶子节点时，数据插入是否正确以及叶子节点分裂是否正确
		/*
		 *Insert.insert(Page curPage, InputDataNode data):54-65 读取最右指针对应的cell，报错。原因：未先判断插入位置索引。
		 *Insert.insert(Page curPage, InputDataNode data):177-188 内部节点无法插入节点，报错。原因：复制粘贴代码，未将curPage改为ParentPage。
		 */
		num = 46+24;
		
		
		/*
		 * 测试在内部节点分裂时否正常，阈值：23*46+22
		 */
		num = 23*46+23;
		
		/*
		 * 大数据量插入，检查是否每一个值都正确插入
		 */
		num = 8000;
		
		/*for(int even=0;even<num; even+=1) {
			main.insert(new InputDataNode(0, even+""));
		}*/
		/*for(int odd=1;odd<num; odd+=2) {
			main.insert(new InputDataNode(0, odd+""));
		}*/
		
		/*
		 * 乱序插入，检查每一个值是否正确插入
		 * 1,num>=3173 节点2093-2015，共23个节点不存在。原因：在维护B+树时，因为添入了parentPsotion，在分裂的时候要使用它。
		 * splitIntoTwo(Page curPage, SplitInfo insertInfo):309-316：确保插入的节点的父亲节点正确。（具体原因不太明了）
		 */
		/*num=40;
		for(int odd=1;odd<num; odd+=2) {
			main.insert(new InputDataNode(0, odd+""));
		}
		
		for(int odd=1;odd<num; odd+=2) {
			main.seek(odd+"");
		}
		
		for(int even=0;even<num; even+=2) {
			main.insert(new InputDataNode(0, even+""));
		}*/
		
		/*for(int even=0;even<num; even+=2) {
			main.seek(even+"");
		}*/
		
		/*rootPage = indexFileAccess.readRootPage();
		System.out.println(rootPage.pageHeader);
		rootPage.showAllKeyCell();
		indexFileAccess.showAllPage(rootPage);*/
		
		/*for(int even=0;even<num; even+=1) {
			main.seek(even+"");
		}*/
		
		//测试seek返回的seekInfo是否正确
		//main.seek("2001");
		
		/*
		 * 测试删除单个节点，不违规。是否影响了其他节点
		 */
		/*String testKey = "25";
		
		//测试是否正确添加到未分配空间
		testKey = "22";
		SeekInfo deleteInfo = main.seek(testKey);
		main.delete(testKey);
		for(int even=0;even<num; even+=1) {
			main.seek(even+"");
		}
		
		//测试是否正确添加自由块
		indexFileAccess.readPage(deleteInfo.wherePage).showAllFreeBlock();
		//测试是否正确分配自由块和未分配区域
		main.insert(new InputDataNode(0, testKey));
		indexFileAccess.readPage(deleteInfo.wherePage).showAllFreeBlock();*/
		
		/*
		 * 当根节点为叶子节点时，多次删除。
		 * 当重新分配空间时
		 * 出现异常页内偏移：1024的空闲块。
		 * 出现bytesNum为0的空闲块。addFreeBlock(short newPageOffset, short newBlockSize):388 下一个freeBlock写为自身。
		 */
		/*rootPage = indexFileAccess.readRootPage();
		System.out.println(rootPage.pageHeader);
		rootPage.showAllKeyCell();
		indexFileAccess.showAllPage(rootPage);
		//rootPage.showAllFreeBlock();
		for(int even=0;even<35; even+=3) {
			main.delete(even+"");
			//main.insert(new InputDataNode(0, even+""));
		}
		rootPage = indexFileAccess.readRootPage();
		System.out.println(rootPage.pageHeader);
		rootPage.showAllFreeBlock();
		for(int even=0;even<35; even+=3) {
			main.insert(new InputDataNode(0, even+""));
		}
		rootPage = indexFileAccess.readRootPage();
		System.out.println(rootPage.pageHeader);
		rootPage.showAllFreeBlock();*/
		
		/**
		 * 根节点1个元素，出现删除时
		 */
		/*main.insert(new InputDataNode(0, "22"));
		rootPage = indexFileAccess.readRootPage();
		System.out.println(rootPage.pageHeader);
		main.delete("22");
		rootPage = indexFileAccess.readRootPage();
		System.out.println(rootPage.pageHeader);*/
		
		/**
		 * 叶子节点合并
		 * delete(String deleteKey, int wherePage, int deleteIndex):
		 * 1，77 未return。导致cellNum为0的根节点，且读取地址为0的父节点，出错。
		 * 2，未修改新的根节点的父亲节点为0.
		 */
		num = 4;
		
		/**
		 * 叶子节点移动
		 */
		num=5;
		
		/**
		 * 内部节点合并
		 */
		num=50000;
		for (int i = 1; i <= num; i++) {
			main.insert(new InputDataNode(0, i+""));
		}
		
		
		main.delete("16");         
		
		for (int i = 1; i <= num; i++) {
			main.seek(i+"");
		}
		rootPage = indexFileAccess.readRootPage();
		System.out.println(rootPage.pageHeader);
		indexFileAccess.showAllLeaf(rootPage);
		indexFileAccess.showAllPage(rootPage);
	}

}
