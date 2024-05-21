package yongin.webapp.news;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.apache.commons.beanutils.BeanUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.List;

//import static jdk.jpackage.internal.IOUtils.getFileName;

@WebServlet(urlPatterns = "/news.nhn")
@MultipartConfig(maxFileSize = 1024*1024*2, location = "/Users/myun/Study")
public class NewsController extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private NewsDAO dao;
    private ServletContext ctx;

    // 웹 리소스 기본 경로 지정
    private final String START_PAGE = "/newsList.jsp";
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        dao = new NewsDAO();
        ctx = getServletContext();
    }

    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("utf-8");
        String action = request.getParameter("action");

        dao = new NewsDAO();

        // 자바 리플렉션을 사용해 if, switch 없이 요청에 따라 구현 메서드가 실행되도록 함.
        Method m;
        String view = null;

        // action 파라미터 없이 접근한 경우
        if (action == null) {
            action = "listNews";
        }

        try {
            // 현재 클래스에서 action 이름과 HttpServletRequest 를 파라미터로 하는 메서드 찾음
            m = this.getClass().getMethod(action, HttpServletRequest.class);

            // 메서드 실행후 리턴값 받아옴
            view = (String)m.invoke(this, request);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            // 에러 로그를 남기고 view 를 로그인 화면으로 지정, 앞에서와 같이 redirection 사용도 가능.
            ctx.log("요청 action 없음!!");
            request.setAttribute("error", "action 파라미터가 잘못 되었습니다!!");
            view = START_PAGE;
        } catch (Exception e) {
            e.printStackTrace();
        }

        // POST 요청 처리후에는 리디렉션 방법으로 이동 할 수 있어야 함.
        if(view.startsWith("redirect:/")) {
            // redirect/ 문자열 이후 경로만 가지고 옴
            String rview = view.substring("redirect:/".length());
            response.sendRedirect(rview);
        } else {
            // 지정된 뷰로 포워딩, 포워딩시 컨텍스트경로는 필요없음.
            RequestDispatcher dispatcher = request.getRequestDispatcher(view);
            dispatcher.forward(request, response);
        }
    }

    public String addNews(HttpServletRequest request) {
        News n = new News();
        try {
            Part part = request.getPart("file");
            String fileName = getFilename(part);
            if (fileName != null && !fileName.isEmpty()) {
                // 웹 애플리케이션의 실제 경로를 얻어옴
                String uploadPath = request.getServletContext().getRealPath("/img");
                File uploadDir = new File(uploadPath);
                if (!uploadDir.exists()) {
                    uploadDir.mkdir(); // 디렉토리가 존재하지 않으면 생성
                }
                // 파일을 지정된 경로에 저장
                part.write(uploadPath + File.separator + fileName);
            }
            // 입력값을 News 객체로 매핑
            BeanUtils.populate(n, request.getParameterMap());

            // 이미지 파일 이름을 News 객체에도 저장
            n.setImg("/img/" + fileName);

            dao.addNews(n);
        } catch (Exception e) {
            e.printStackTrace();
            ctx.log("뉴스 추가 과정에서 문제 발생!!");
            request.setAttribute("error", "뉴스가 정상적으로 등록되지 않았습니다!!");
            return listNews(request);
        }

        return "redirect:/news.nhn?action=listNews";
    }

        public String listNews(HttpServletRequest request) {
        List<News> list;
        try {
            list = dao.getAll();
            request.setAttribute("newslist", list);
        } catch (Exception e) {
            e.printStackTrace();
            ctx.log("뉴스 목록 생성 과정에서 문제 발생!!");
            request.setAttribute("error", "뉴스 목록이 정상적으로 처리되지 않았습니다!!");
        }
        return "/newsList.jsp";
    }

    public String getNews(HttpServletRequest request) {
        int aid = Integer.parseInt(request.getParameter("aid"));

        try {
            News n = dao.getNews(aid);
            request.setAttribute("news", n);
            System.out.println("이미지 경로" + n.getImg());
        } catch (SQLException e) {
            e.printStackTrace();
            ctx.log("뉴스를 가져오는 과정에서 문제 발생!!");
            request.setAttribute("error", "뉴스를 정상적으로 가져오지 못했습니다!!");
        }
        return "/newsView.jsp";
    }

    public String deleteNews(HttpServletRequest request) {
        int aid = Integer.parseInt(request.getParameter("aid"));
        try {
            dao.delNews(aid);
        } catch (SQLException e) {
            e.printStackTrace();
            ctx.log("뉴스 삭제 과정에서 문제 발생!!");
            request.setAttribute("error", "뉴스가 정상적으로 삭제ㅔ되지 않았습니다!!!");
            return listNews(request);
        }
        return "redirect:/news.nhn?action=listNews";
    }

    // multipart 헤더에서 파일이름 추출
    private String getFilename(Part part) {
        String fileName = null;
        // 파일이름이 들어있는 헤더 영역을 가지고 옴
        String header = part.getHeader("content-disposition");
        //part.getHeader -> form-data; name="img"; filename="사진5.jpg"
        System.out.println("Header => "+header);

        // 파일 이름이 들어있는 속성 부분의 시작위치를 가져와 쌍따옴표 사이의 값 부분만 가지고옴
        int start = header.indexOf("filename=");
        fileName = header.substring(start+10,header.length()-1);
        ctx.log("파일명:"+fileName);
        return fileName;
    }
}
