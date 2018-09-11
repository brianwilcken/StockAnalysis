package webapp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
public class StockAnalysisApplication extends SpringBootServletInitializer {

	final static Logger logger = LogManager.getLogger(StockAnalysisApplication.class);
	
	@Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(StockAnalysisApplication.class);
    }
	
	public static void main(String[] args) {
		logger.info("Application Startup");
		SpringApplication.run(StockAnalysisApplication.class, args);
	}

//	@Override
//	protected Class<?>[] getConfigClasses() {
//		return new Class[] {
//				EventsRouter.class,
//				WebConfig.class,
//				CorsConfiguration.class
//		};
//	}
}
