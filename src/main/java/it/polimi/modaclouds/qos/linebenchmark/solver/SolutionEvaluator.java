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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.apache.commons.lang3.time.StopWatch;

public class SolutionEvaluator implements Runnable, ActionListener{

	// Return values of lqns
	/** The Constant LQNS_RETURN_SUCCESS. */
	private static final int LQNS_RETURN_SUCCESS = 0;
	
	/** The Constant LQNS_RETURN_MODEL_FAILED_TO_CONVERGE. */
	private static final int LQNS_RETURN_MODEL_FAILED_TO_CONVERGE = 1;

	/** The Constant LQNS_RETURN_INVALID_INPUT. */
	private static final int LQNS_RETURN_INVALID_INPUT = 2;

	/** The Constant LQNS_RETURN_FATAL_ERROR. */
	private static final int LQNS_RETURN_FATAL_ERROR = -1;

	private LineServerHandler handler;

	Path filePath; 
	Path resultfilePath;	
	String solver;
	//LqnResultParser resultParser;	
	ArrayList<ActionListener> listeners = new ArrayList<>();

	public SolutionEvaluator(Path instance, String solver) {
		this.solver = solver;
		filePath = instance;
	}

	public void setLineServerHandler(LineServerHandler handler){
		this.handler=handler;
	}

	public void addListener(ActionListener listener){
		listeners.add(listener);		
	}


	public void removeListener(ActionListener listener){
		listeners.remove(listener);		
	}

	public void run() {

		//run the evaluator
		if(solver.equals(Main.LQNS_SOLVER))
			runWithLQNS();		
		//Evaluate with LINE
		else
			runWithLINE();

		//parse the results
		//parseResults();
	}

	public void parseResults(){		
		if(solver.equals(Main.LQNS_SOLVER)){
			resultfilePath = Paths.get(filePath.toString().substring(0,filePath.toString().lastIndexOf('.'))+".lqxo");
			//resultParser = new LQNSResultParser(resultfilePath);
		}
		else{
			resultfilePath = Paths.get(filePath.toString().substring(0,filePath.toString().lastIndexOf('.'))+"_res.xml");
			//resultParser = new LINEResultParser(resultfilePath);
		}

		//parse the results and save them
	}

	private void runWithLINE(){
		if(handler==null){
			System.err.println("LINE server handle not initialized");
			return;
		}
		handler.addListener(filePath,this);
		handler.solve(filePath, null);

	}


	private void runWithLQNS(){
		StopWatch timer = new StopWatch();
		String solverProgram = "lqns";

		String command = solverProgram+" "+filePath+" -f"; //using the fast option
		System.out.println("Launch: "+command);
		//String command = solverProgram+" "+filePath; //without using the fast option
		try {		
			ProcessBuilder pb = new ProcessBuilder(splitToCommandArray(command));

			//start counting
			timer.start();
			Process proc = pb.start();
			readStream(proc.getInputStream(),false);
			readStream(proc.getErrorStream(),true);
			int exitVal = proc.waitFor();
			//stop counting
			timer.stop();
			proc.destroy();



			//evaluation error messages
			if(exitVal == LQNS_RETURN_SUCCESS);
			else if (exitVal == LQNS_RETURN_MODEL_FAILED_TO_CONVERGE) {
				System.err.println(Main.LQNS_SOLVER
						+ " exited with "
						+ exitVal
						+ ": The model failed to converge. Results are most likely inaccurate. ");
				System.err.println("Analysis Result has been written to: " + resultfilePath);
			} else {
				String message = "";
				if (exitVal == LQNS_RETURN_INVALID_INPUT) {
					message = solverProgram + " exited with " + exitVal
							+ ": Invalid Input.";
				} else if (exitVal == LQNS_RETURN_FATAL_ERROR) {
					message = solverProgram + " exited with " + exitVal
							+ ": Fatal error";
				} else {
					message = solverProgram
							+ " returned an unrecognised exit value "
							+ exitVal
							+ ". Key: 0 on success, 1 if the model failed to meet the convergence criteria, 2 if the input was invalid, 4 if a command line argument was incorrect, 8 for file read/write problems and -1 for fatal errors. If multiple input files are being processed, the exit code is the bit-wise OR of the above conditions.";
				}
				System.err.println(message);
			}					
		} catch (IOException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//tell listeners that the evaluation has been performed
		EvaluationCompletedEvent evaluationCompleted= new EvaluationCompletedEvent(this,  0,  null);
		evaluationCompleted.setEvaluationTime(timer.getTime());
		evaluationCompleted.setSolverName(solver);
		evaluationCompleted.setModelPath(filePath.getFileName());
		for(ActionListener l:listeners)
			l.actionPerformed(evaluationCompleted);
	}


	private String[] splitToCommandArray(String command) {
		return command.split("\\s");
	}

	private void readStream(InputStream is,boolean show) {
		try {
			InputStreamReader isr = new InputStreamReader(is);
			BufferedReader br = new BufferedReader(isr);
			String line = null;
			while ((line = br.readLine()) != null){
				if(show)
					System.out.println("Pb: "+line);
			}

		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		for(ActionListener l:listeners)
			l.actionPerformed(e);
	}
}
