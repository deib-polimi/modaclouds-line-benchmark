/**
 * Copyright 2014 deib-polimi
 * Contact: deib-polimi <giovannipaolo.gibilisco@polimi.it>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package it.polimi.modaclouds.qos.linebenchmark.main;

import it.polimi.modaclouds.qos.linebenchmark.solver.EvaluationServer;

import java.io.File;
import java.io.FilenameFilter;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

	public static final String LINE_SOLVER = "LINE";
	public static final String LQNS_SOLVER = "LQNS";
	public static Path LINE_PROP_FILE = Paths.get("line.properties");
	private static Path MODEL_FOLDER = Paths.get("models");

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			LINE_PROP_FILE = Paths.get(Main.class.getResource("/LINE.properties").toURI());
		} catch (URISyntaxException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		System.out.println("Launching Solvers");
		System.out.println("LINE prop file: " + LINE_PROP_FILE.toAbsolutePath());
		EvaluationServer server = new EvaluationServer(true);
		File modelFolderFile = MODEL_FOLDER.toFile();

		if (!modelFolderFile.exists() && modelFolderFile.isDirectory()) {
			System.err.println("Model folder missing");
			return;
		}

		File[] modelFiles = modelFolderFile.listFiles(new FilenameFilter() {

			@Override
			public boolean accept(File arg0, String arg1) {
				return arg1.endsWith(".xml") && !arg1.endsWith("_res.xml");
			}
		});

		for (File f : modelFiles) {
			server.evaluateModel(f.toPath(), LQNS_SOLVER);
		}

		try {
			while (!server.lqnsEvaluationsFinished())
				Thread.sleep(10 * 1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		for (File f : modelFiles) {
			server.evaluateModel(f.toPath(), LINE_SOLVER);
		}

		try {
			while (!server.lineEvaluationsFinished())
				Thread.sleep(10 * 1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		server.exit();
		server.logResults();
		System.out.println("Terminated");

		return;
	}

}
