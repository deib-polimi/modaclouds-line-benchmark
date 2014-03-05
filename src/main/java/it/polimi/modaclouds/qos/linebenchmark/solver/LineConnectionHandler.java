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

import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.time.StopWatch;

public class LineConnectionHandler implements Runnable {
	private BufferedReader in;
	private boolean read = true;
	private boolean running = false;
	private boolean connected = false;
	private Map<Path,String> evaluations = new HashMap<Path, String>();
	private Map<Path,StopWatch> timers= new HashMap<Path, StopWatch>();
	private ArrayList<ActionListener> listeners = new ArrayList<ActionListener>();
	String prefix="";

	public LineConnectionHandler(BufferedReader in, String prefix) {
		this.in = in;
		if(prefix != null)
			this.prefix = prefix; 
	}
	public synchronized void close(){
		read = false;
	}

	public void addListener(ActionListener listener){
		listeners.add(listener);
	}

	private synchronized boolean isRead() {
		return read;
	}

	public synchronized boolean isRunning() {
		return running;
	}

	public synchronized boolean isConnected(){
		return connected;
	}

	public void run() {
		while(isRead())
			try {
				Thread.sleep(10);
				if(in.ready()){
					String line = in.readLine();				
					//System.out.println("LINE "+prefix+": "+line);

					//set the starting
					if(line.contains("MODEL"))
						updateModelEvaluation(line);
					if(line.contains("Listening on port"))
						setRunning(true);
					if(line.contains("LINE READY"))
						setConnected(true);
					if(line.contains("LINE STOP"))
						setRunning(false);

				}

			} catch (IOException e) {
				if(e.getMessage().equals("Stream closed"))
					System.out.println("LINE "+prefix+": "+e.getMessage());
				else 
					e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}

	private synchronized void setRunning(boolean running){
		this.running = running;
	}

	private synchronized void setConnected(boolean connected){
		this.connected = connected;
	}


	private synchronized void updateModelEvaluation(String message){
		message = message.trim().replaceAll(" +", " ");		
		String[] tokens = message.split(" ");
		String modelName = tokens[1];		
		modelName = modelName.replace("_res.xml", ".xml");
		modelName = Paths.get(modelName).toString();
		String status = null;		
		if(tokens.length == 4)
			status = tokens[3];
		else
			status = tokens[2];		
		Path modelPath = Paths.get(modelName);
		evaluations.put(modelPath,status);

		StopWatch timer; 
		if(status.equals("SUBMITTED")){
			timer = new StopWatch();		
			timers.put(modelPath, timer);
			timer.start();
		}else{
			timer = timers.get(modelPath);
			timer.stop();

			EvaluationCompletedEvent evaluationCompleted= new EvaluationCompletedEvent(this,  0,  null);
			evaluationCompleted.setEvaluationTime(timer.getTime());
			evaluationCompleted.setSolverName(Main.LINE_SOLVER);
			evaluationCompleted.setModelPath(modelPath.getFileName());
			for(ActionListener l:listeners)
				l.actionPerformed(evaluationCompleted);
		}
	}



}
