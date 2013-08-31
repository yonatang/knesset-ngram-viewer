package idd.nlp.ok.viewer;

import java.sql.SQLException;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.h2.Driver;
import org.h2.tools.Server;

public class StartupConfig implements ServletContextListener {

	private Server tcpServer,webServer;
	@Override
	public void contextDestroyed(ServletContextEvent event) {
		System.out.println("Shutting down the database");
		if (tcpServer!=null){
			tcpServer.stop();
		}
		if (webServer!=null){
			webServer.stop();
		}
		Driver.unload();
	}

	@Override
	public void contextInitialized(ServletContextEvent event) {
		System.out.println("Initiating database");
		Driver.load();
		try {
			tcpServer=Server.createTcpServer().start();
			webServer=Server.createWebServer().start();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

}
