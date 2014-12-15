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
	 * �������ļ�����ʼ��Insert
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
			//����ÿ��Page����cell�������������ݿ�
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
		
		//���Բ��뵥�����ݽڵ��Ƿ���ȷ
		/*InputDataNode data = new InputDataNode(0, "255");
		main.insert(data);
		rootPage = indexFileAccess.readRootPage();
		System.out.println(rootPage.pageHeader);
		rootPage.showAllKeyCell();*/
		
		//���Բ��뵫�����ѡ�45���ٽ�ֵ
		int num = 45;

		
		//���Ը��ڵ�ķ��ѣ����ݲ����Ƿ���ȷ
		num = 46;
		
		//���Ե����ڵ㲻��Ҷ�ӽڵ�ʱ�����ݲ����Ƿ���ȷ�Լ�Ҷ�ӽڵ�����Ƿ���ȷ
		/*
		 *Insert.insert(Page curPage, InputDataNode data):54-65 ��ȡ����ָ���Ӧ��cell������ԭ��δ���жϲ���λ��������
		 *Insert.insert(Page curPage, InputDataNode data):177-188 �ڲ��ڵ��޷�����ڵ㣬����ԭ�򣺸���ճ�����룬δ��curPage��ΪParentPage��
		 */
		num = 46+24;
		
		
		/*
		 * �������ڲ��ڵ����ʱ����������ֵ��23*46+22
		 */
		num = 23*46+23;
		
		/*
		 * �����������룬����Ƿ�ÿһ��ֵ����ȷ����
		 */
		num = 8000;
		
		/*for(int even=0;even<num; even+=1) {
			main.insert(new InputDataNode(0, even+""));
		}*/
		/*for(int odd=1;odd<num; odd+=2) {
			main.insert(new InputDataNode(0, odd+""));
		}*/
		
		/*
		 * ������룬���ÿһ��ֵ�Ƿ���ȷ����
		 * 1,num>=3173 �ڵ�2093-2015����23���ڵ㲻���ڡ�ԭ����ά��B+��ʱ����Ϊ������parentPsotion���ڷ��ѵ�ʱ��Ҫʹ������
		 * splitIntoTwo(Page curPage, SplitInfo insertInfo):309-316��ȷ������Ľڵ�ĸ��׽ڵ���ȷ��������ԭ��̫���ˣ�
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
		
		//����seek���ص�seekInfo�Ƿ���ȷ
		//main.seek("2001");
		
		/*
		 * ����ɾ�������ڵ㣬��Υ�档�Ƿ�Ӱ���������ڵ�
		 */
		/*String testKey = "25";
		
		//�����Ƿ���ȷ��ӵ�δ����ռ�
		testKey = "22";
		SeekInfo deleteInfo = main.seek(testKey);
		main.delete(testKey);
		for(int even=0;even<num; even+=1) {
			main.seek(even+"");
		}
		
		//�����Ƿ���ȷ������ɿ�
		indexFileAccess.readPage(deleteInfo.wherePage).showAllFreeBlock();
		//�����Ƿ���ȷ�������ɿ��δ��������
		main.insert(new InputDataNode(0, testKey));
		indexFileAccess.readPage(deleteInfo.wherePage).showAllFreeBlock();*/
		
		/*
		 * �����ڵ�ΪҶ�ӽڵ�ʱ�����ɾ����
		 * �����·���ռ�ʱ
		 * �����쳣ҳ��ƫ�ƣ�1024�Ŀ��п顣
		 * ����bytesNumΪ0�Ŀ��п顣addFreeBlock(short newPageOffset, short newBlockSize):388 ��һ��freeBlockдΪ����
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
		 * ���ڵ�1��Ԫ�أ�����ɾ��ʱ
		 */
		/*main.insert(new InputDataNode(0, "22"));
		rootPage = indexFileAccess.readRootPage();
		System.out.println(rootPage.pageHeader);
		main.delete("22");
		rootPage = indexFileAccess.readRootPage();
		System.out.println(rootPage.pageHeader);*/
		
		/**
		 * Ҷ�ӽڵ�ϲ�
		 * delete(String deleteKey, int wherePage, int deleteIndex):
		 * 1��77 δreturn������cellNumΪ0�ĸ��ڵ㣬�Ҷ�ȡ��ַΪ0�ĸ��ڵ㣬����
		 * 2��δ�޸��µĸ��ڵ�ĸ��׽ڵ�Ϊ0.
		 */
		num = 4;
		
		/**
		 * Ҷ�ӽڵ��ƶ�
		 */
		num=5;
		
		/**
		 * �ڲ��ڵ�ϲ�
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
