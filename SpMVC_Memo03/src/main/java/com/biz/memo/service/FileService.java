package com.biz.memo.service;

import java.io.File;
import java.util.List;
import java.util.UUID;

import javax.servlet.ServletContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.biz.memo.mapper.FileDao;
import com.biz.memo.mapper.MemoDao;
import com.biz.memo.model.FileVO;
import com.biz.memo.model.MemoVO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FileService {
	
	/*
	 * ServletContext는 Tomcat이 실행되는 환경에서
	 * 다양한 정보를 가지고 있는 매우 중요한 클래스
	 * context로 부터 서버에 대한 정보를 추출해 볼수 있다.
	 */
	@Autowired
	ServletContext context;
	
	@Autowired
	MemoDao mDao;
	
	@Autowired
	FileDao fDao;
	
	/*
	 * @Transactional Annotation을 사용하여
	 * tbl_memo 와 tbl_file table에 insert update를 보장하자
	 * 이 Annotation을 사용하기 위해서
	 * mybatis-context.xml에 transaction 설정을 해 두어야 한다.
	 */
	@Transactional
	public String fileUpload(MemoVO memoVO, 
				MultipartFile m_file) {
		
		// 업로드할 원래 파일 이름을 추출
		String fileName = m_file.getOriginalFilename();
		String saveFileName = "";
		
		// tomcat server가 실행해서
		// 보여주는 context root가 어디냐
		String realPath = context.getRealPath("/");
		
		// http://localhost:8080/memo03/files/ 문자열을 생성
		String upLoadPath = realPath + "files/";
		
		log.debug("realPath: " + realPath);
		log.debug("upLoadPath: " + upLoadPath);
		
		// 만약 파일이 업로드 되지 않은 상태에서 upload등을 수행하면
		// 많은 오류가 발생하고
		// 실제 환경에서는 보안에 매우 위험할 수 있다.
		// 다양한 방법으로 안전하게 업로드가 되는지 확인 해야 한다.
		if(m_file != null) {
			
			// serlvet-context에서 파일의 크기를 제한 했지만
			// 한번 더 허용한 용량보다 크지 않은지 검사를 한다.
			// 혹시 지정된 크기보다 큰 파일이 업로드 되면
			// 무시 해 버리자.
			if(m_file.getSize() > 1048576) {
				return null ;
			}
			
			// 파일이름을 검사해서 null이 아니고, "" 아닌경우만
			// 처리 하겠다.
			if(fileName != null && !fileName.equals("")) {
				
				/*
				 * UUID : universally Unique Identified
				 * 범용 유일 식별자
				 * 유일한 단독 키값 
				 * 16진수 32자리의 임의 키값을 생성
				 * 16의 32승 만큼 임의 값중 하나 생성
				 * 340,282,366,920,938,463,463,374,607,431,768,211,456
				 * 
				 * 오리지널 파일 이름으로 업로드를 하면
				 * 혹시 악의적인 사용자에 의해서 파일이 변조 될수 있으므로
				 * 업로드할때는 임의 키값을 생성해서 파일 이름을 변경하여
				 * 업로드들 수행한다.
				 * 
				 * UUID는 자바5(1.5)부터 사용가능하고
				 * 그전에는 timestampe
				 */
				saveFileName 
					= UUID.randomUUID().toString() + fileName ;
				
				// 파일을 업로드할 폴더가 만들어 있는지 검사하여
				// 없으면 생성을 하도록 
				File dir = new File(upLoadPath);
				if(!dir.exists()) { // 폴더가 자동으로 생성되지 않았으면
					dir.mkdir() ; // 폴더를 만들어라
				}
				
				// 운영체제 독립적인 구조의 파일 이름 체계를 생성
				File upLoadFile 
					= new File(upLoadPath, saveFileName);
				try {
					// nio 팩키지의 File(1.7이상)클래스의 도움을 받아
					// 사용자 컴퓨터에서 Tomcat 서버의 디렉토리로
					// 파일을 한번 전송할수 있게 되었다.
					m_file.transferTo(upLoadFile);
					
				} catch (Exception e) {
					e.printStackTrace();
				} //tr
			} // if
			
			// tbl_files 테이블에 파일 정보를 저장할 예정

			if(memoVO.getId() > 0)
				mDao.update(memoVO);
			else
				mDao.insert(memoVO);
			
			// Builder pattern
			// fileVO 객체를 생성하면서
			// 각 속성(필드, 맴버변수)의 값을 세팅하고자 할때
			// 사용하는 방법
			FileVO fileVO = FileVO.builder()
					.parent_id(memoVO.getId())
					.file_name(fileName)
					.save_file_name(saveFileName).build();
			
			// 생성자에 직접 속성값을 전달하면서 객체를 생성하는
			// 생성자 pattern
			// fileVO 
			// = new FileVO(0L,memoVO.getId(),fileName,saveFileName);
			
			// 기존에 setter를 사용한 pattern
			// fileVO = new FileVO();
			// fileVO.setParent_id(memoVO.getId());
			fDao.insert(fileVO);
			return saveFileName;
			
		} // if
		
		return null;
	} // end fileUpLoad

	/*
	 * 첨부파일 삭제
	 * 1. tbl_files 테이블의 id값으로 tbl_files의 레코드를 가져오기
	 * 2. fileVO에 담기
	 * 3. fileVO의 save_file_name 값을 가지고
	 * 4. 실제 서버에 저장된 파일을 삭제
	 * 5. tbl_files 테이블의 해당되는 정보를 삭제
	 */
	public int delete(long id) {
		
		// tble_files 의 id값으로 파일정보 가져오기
		FileVO fVO = fDao.findById(id);
		
		// 파일정보로 부터 실제 저장된 파일 이름을 추출
		String realFile = fVO.getSave_file_name();
		
		// 파일이 저장된 서버의 실제 폴더
		String realPath = context.getRealPath("/files/");
		
		// 저장된 폴더와 파일 이름을 연결하여 파일 정보 생성
		File file = new File(realPath,realFile);
		
		boolean fileDelOk = false;
		if(file.exists()) // 파일이 실제로 존재 하냐
			fileDelOk = file.delete(); // 파일 삭제 성공하면 true, 실패하면 false
	
		String fileDelMsg = "";
		
		// 일반적인 if 문 코드
		if(fileDelOk) fileDelMsg = "삭제성공";
		else fileDelMsg = "삭제실패";
		log.debug("파일삭제 OK ? : " + fileDelMsg);

		// 3항연산자 코드
		String fileDelMsg2 = fileDelOk ? "삭제성공" : "삭제실패" ;
		fileDelMsg2 = fileDelOk == true ? "삭제성공" : "삭제실패";
		
		log.debug("파일삭제 OK ? : " + (fileDelOk ? "삭제성공" : "삭제실패"));
		
		// r 변수를 임의로 생성해서
		// 짝인경우만 보고싶다
		int r = (int)(Math.random() * 100) + 1;
		if( r % 2 == 0) log.debug("짝수:" + r);
		else log.debug("짝수 아님");

		log.debug((r%2 == 0 ? "짝수" + r : "짝수아님"));

		// 실제 파일이 삭제 된것을 확인 한 후 table에 파일 정보를 삭제
		if(fileDelOk) return fDao.delete(id);
		else return 0;
	
	}

	public void fileDelete(long id) {

		// id = tbl_files 입장에서는 parent_id 이다.
		List<FileVO> fileList = fDao.selectById(id);
		
		String realPath = context.getRealPath("/files/");
		
		for(FileVO v : fileList) {
			String realFile = v.getSave_file_name();
			File file = new File(realPath, realFile);
			if(file.exists())
				file.delete();
		}
	}
	 
} // end class
