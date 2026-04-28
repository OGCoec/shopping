import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.util.matcher.*;

public class MatcherCheck {
  public static void main(String[] args) {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/sh");
    RequestMatcher[] matchers = new RequestMatcher[] {
      new AntPathRequestMatcher("/"),
      new AntPathRequestMatcher("/index.html"),
      new AntPathRequestMatcher("/shopping/**"),
      new AntPathRequestMatcher("/error"),
      new AntPathRequestMatcher("/css/**")
    };
    String[] patterns = {"/", "/index.html", "/shopping/**", "/error", "/css/**"};
    for (int i = 0; i < matchers.length; i++) {
      System.out.println(patterns[i] + " => " + matchers[i].matches(req));
    }
    OrRequestMatcher orMatcher = new OrRequestMatcher(matchers);
    System.out.println("OR => " + orMatcher.matches(req));
  }
}
