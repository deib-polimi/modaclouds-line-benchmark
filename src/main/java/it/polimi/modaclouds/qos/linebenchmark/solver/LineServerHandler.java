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
package it.polimi.modaclouds.qos.linebenchmark.solver;


import it.polimi.modaclouds.qos.linebenchmark.main.Main;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LineServerHandler implements ActionListener {
	/** LINE connection handlers **/
	private Socket lineSocket = null;
	private boolean localInstance = false;
	private PrintWriter out = null;
	private Process proc;
	private BufferedReader processIn = null;
	private LineConnectionHandler processLog;
	private BufferedReader socketIn = null;
	private LineConnectionHandler socketLog;	
	private Map<Path,ActionListener> listeners = new HashMap<Path, ActionListener>();
	private static final Logger logger = LoggerFactory.getLogger(LineServerHandler.class);
	public void closeConnections() {
		if (out != null)
			out.close();

		try {
			if (processLog != null && processIn != null) {
				processLog.close();
				processIn.close();
			}
			if (socketLog != null && socketIn != null) {
				socketLog.close();
				socketIn.close();
			}
			if (lineSocket != null)
				lineSocket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void connectToLINEServer() {
		connectToLINEServer(null, -1);
	}

	public void connectToLINEServer(String host, int port) {

		Properties lineProperties = new Properties();

		File linePropFile = Main.LINE_PROP_FILE.toFile();
		File directory = null;
		try {

			FileInputStream propInput = new FileInputStream(linePropFile);
			lineProperties.load(propInput);
			propInput.close();
			if (host == null)
				host = lineProperties.getProperty("host", "localhost");
			if (port == -1)
				port = Integer.parseInt(lineProperties.getProperty("port",
						"5463"));
			directory = new File(lineProperties.getProperty("directory", null));

			// try to connect
			initLINEConnection(host, port);
		} catch (UnknownHostException e) {
			// fallback to local host and retry
			if (host != "localhost") {
				closeConnections();
				logger.info("Don't know about host:" + host
						+ ". Switching to localhost and trying reconnection.");
				host = "localhost";
				connectToLINEServer(host, port);
			}
		} catch (IOException e) {
			closeConnections();
			// fall back to local host and launch LINE
			System.out
					.println("Could not connect to LINE on host: "
							+ host
							+ " on port: "
							+ port
							+ "\ntrying to launch line locally and connect to localhost.");
			launchLine(linePropFile, directory);
			host = "localhost";
			try {
				initLINEConnection(host, port);
			} catch (IOException e1) {
				closeConnections();
				System.err
						.println("Could not connect to local instance of LINE");
				e1.printStackTrace();
			}
		}
	}

	private void initLINEConnection(String host, int port) throws IOException {
		lineSocket = new Socket(host, port);
		out = new PrintWriter(lineSocket.getOutputStream());
		if (socketIn == null)
			socketIn = new BufferedReader(new InputStreamReader(
					lineSocket.getInputStream()));
		if (socketLog == null) {
			socketLog = new LineConnectionHandler(socketIn, "socket");
			socketLog.addListener(this);
			(new Thread(socketLog)).start();
		}
		while (!socketLog.isConnected())
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		logger.info("Connected to LINE on " + host + ":" + port);
	}
	

	private boolean launchLine(File linePropFile, File directory) {
		try {
			String lineInvocation = "LINE" + " " + "\""
					+ linePropFile.getAbsolutePath().replace('\\', '/') + "\"";
			logger.info(lineInvocation);
			ProcessBuilder pb = new ProcessBuilder(lineInvocation.split("\\s"));
			pb.directory(directory);
			pb.redirectErrorStream(true);
			proc = pb.start();
			processIn = new BufferedReader(new InputStreamReader(
					proc.getInputStream()));
			processLog = new LineConnectionHandler(processIn, "process");
			(new Thread(processLog)).start();
			while (!processLog.isRunning())
				Thread.sleep(100);
			;
			localInstance = true;

			// the startup has ended
			return true;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}

	public void solve(Path filePath, Path REfilePath) {
		
		// build the command
		String command = "SOLVE " + filePath.toAbsolutePath();
		if (REfilePath != null)
			command += " " + REfilePath.toAbsolutePath();	
		// send the command
		out.println(command);
		out.flush();
		return;

	}

	public void terminateLine() {
		if (localInstance) {
			out.println("QUIT");
			out.flush();
			try {
				proc.waitFor();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		closeConnections();

	}

	public void addListener(Path filePath, SolutionEvaluator solutionEvaluator) {
		listeners.put(filePath.getFileName(), solutionEvaluator);
		logger.info("added listener:"+filePath.getFileName());
	}

	public void actionPerformed(ActionEvent e) {
		if(e instanceof EvaluationCompletedEvent){
			EvaluationCompletedEvent event = (EvaluationCompletedEvent) e;
			logger.debug("Evaluation completed on model: "+event.getModelPath()+" solver: "+event.getSolverName());
			listeners.get(event.getModelPath().getFileName()).actionPerformed(e);
		}
		
	}
}
