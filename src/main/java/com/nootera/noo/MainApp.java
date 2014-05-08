/*
 * Copyright Â© 2014 BownCo
 * All rights reserved.
 */

package com.nootera.noo;

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
        stage.setTitle("Noo");
		stage.initStyle(StageStyle.UNIFIED);
		//stage.initStyle(StageStyle.UNDECORATED);
		//stage.setOpacity(0.2);
        //stage.setResizable(true); //doesn't work with StageStyle.UNDECORATED
        stage.centerOnScreen();
		//stage.setFullScreen(true);
		
        Parent root = FXMLLoader.load(getClass().getResource("/fxml/Scene.fxml"));
        //Parent root = new Parent() {};
        //root.setDepthTest(DepthTest.ENABLE); //3D
		
        Scene scene = new Scene(root);
        scene.getStylesheets().add("/styles/Styles.css");
        //scene.setCamera(new PerspectiveCamera()); //3D
        stage.setScene(scene);
		stage.setOnCloseRequest(new EventHandler<WindowEvent>() {
			@Override public void handle(WindowEvent e) { onClose(e); }
		});
//		stage.setOnShown(new EventHandler<WindowEvent>() {
//			@Override public void handle(WindowEvent e) { onShown(e); }
//		});
        stage.show();
		
//		root = FXMLLoader.load(getClass().getResource("/fxml/Scene.fxml"));
//		scene.setRoot(root);
		
//		//these only have values here
//		double stageH = stage.getHeight();
//		double stageW = stage.getWidth();
//		double rootH = root.getLayoutBounds().getHeight();
//		double rootW = root.getLayoutBounds().getWidth();
//		double sceneH = scene.getHeight();
//		double sceneW = scene.getWidth();
    }
//	public void onShown(WindowEvent e) {
//		System.out.println("onShown");
//	}
	public void onClose(WindowEvent e) {
		System.out.println("onClose");
		if (FXMLController.instance != null) {
			FXMLController.instance.close();
		}
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
