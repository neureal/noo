/*
 * Copyright © 2014 BownCo
 * All rights reserved.
 */

package com.nootera.trader;

import javafx.application.Application;
import static javafx.application.Application.launch;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;


public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        stage.setTitle("Trader");
		stage.initStyle(StageStyle.UNDECORATED);
        stage.centerOnScreen();
		
        Parent root = FXMLLoader.load(getClass().getResource("/fxml/Scene.fxml"));
		
        Scene scene = new Scene(root);
        scene.getStylesheets().add("/styles/Styles.css");
        stage.setScene(scene);
		stage.setOnShown(new EventHandler<WindowEvent>() {
			@Override public void handle(WindowEvent e) { onShown(e); }
		});
        stage.show();
    }
	public void onShown(WindowEvent e) {
		System.out.println("onShown");
		
	}

    /**
     * The main() method is ignored in correctly deployed JavaFX application.
     * main() serves only as fallback in case the application can not be
     * launched through deployment artifacts, e.g., in IDEs with limited FX
     * support. NetBeans ignores main().
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
