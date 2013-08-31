package idc.nlp.ok.protocol.parser;


import idc.nlp.ok.model.Protocol;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ParserTest {
	private ProtocolParser pp;
	@BeforeClass
	public void setup(){
		pp=new ProtocolParser();
	}
	
	@Test
	public void shouldReadDate() throws Exception{
		SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd");
		
		Protocol p=pp.parse(getClass().getResourceAsStream("/7225.xml"));
		Assert.assertEquals("2013-05-27", sdf.format(p.getDate()));
		
		p=pp.parse(getClass().getResourceAsStream("/7340.xml"));
		Assert.assertEquals("2013-06-17", sdf.format(p.getDate()));
		
	}
	
	@Test
	public void shouldReadUrl() throws Exception {
		
		Protocol p=pp.parse(new InputStreamReader(getClass().getResourceAsStream("/7340.xml"),Charset.forName("UTF8")));
		Assert.assertEquals("/plenum/7340/", p.getApiUrl());
	}
	
//	@Test
//	public void temp() throws Exception {
//		Collection<File> c=FileUtils.listFiles(new File("../nlp-fp-cruncher/data/plenums"), new String[]{"xml"},false);
//		for (File f:c){
//			try(FileInputStream fis=new FileInputStream(f)){
//				Protocol p=pp.parse(new InputStreamReader(fis,Charset.forName("UTF8")));
//				System.out.println(f.getName()+" - "+p.getApiUrl());
//			}
//		}
//		
//	}
	
	@Test
	public void t() throws Exception {
		ProtocolParser mt = new ProtocolParser();
		Protocol p=mt.parse(this.getClass().getResourceAsStream("/7225.xml"));
		System.out.println(p);
		System.out.println(p.getDate());
	}
	
}
