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
/**
 * 
 */
package it.polimi.modaclouds.qos.linebenchmark.solver;


import it.polimi.modaclouds.qos.linebenchmark.main.Main;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Michele Ciavotta
 * 
 */
public class EvaluationServer implements ActionListener {

	private ThreadPoolExecutor executor;
	private LineServerHandler handler;
	private Map<Path, Long> lineEvaluationTimes = new HashMap<Path, Long>();
	private Map<Path, Long> lqnsEvaluationTimes = new HashMap<Path, Long>();
	//private int nMaxThreads = Runtime.getRuntime().availableProcessors(); //does not allow the queue to grow, should wnsure immediate evaluation
	private int nMaxThreads = 200;

	private BlockingQueue<Runnable> queue = new SynchronousQueue<Runnable>();
	private int totalNumberOfEvaluations = 0;

	private int pendingLineEvaluations = 0;
	private int pendingLqnsEvaluations = 0;

	private boolean parallel = true;
	private final Logger logger = LoggerFactory.getLogger("Times Logger");

	/**
	 * 
	 */
	public EvaluationServer(boolean parallel) {

		this.parallel = parallel;
		if (!parallel)
			nMaxThreads = 1;
		// initialize the thread pool
		executor = new ThreadPoolExecutor(1, nMaxThreads, 200,
				TimeUnit.MILLISECONDS, queue);

		// launch LINE
		handler = new LineServerHandler();
		handler.connectToLINEServer();

	}

	@Override
	public synchronized void actionPerformed(ActionEvent e) {
		if (e instanceof EvaluationCompletedEvent) {
			EvaluationCompletedEvent completion = (EvaluationCompletedEvent) e;
			if (completion.getSolverName().equals(Main.LINE_SOLVER)){
				lineEvaluationTimes.put(completion.getModelPath(),
						completion.getEvaluationTime());
				decrementPendingLineEvaluations();
			}
			if (completion.getSolverName().equals(Main.LQNS_SOLVER)){
				lqnsEvaluationTimes.put(completion.getModelPath(),
						completion.getEvaluationTime());
				decrementPendingLqnsEvaluations();
			}
		}
	}

	public void evaluateModel(Path lqnModelPath, String solver) {

		// build the evaluation object
		SolutionEvaluator eval = new SolutionEvaluator(lqnModelPath, solver);
		eval.addListener(this);

		if (solver.equals(Main.LINE_SOLVER)) {
			eval.setLineServerHandler(handler);
			incrementPendingLineEvaluations();
		} else
			incrementPendingLqnsEvaluations();

		// launch the evaluation
		if(parallel)
			executor.execute(eval);
		else 
			eval.run();
	}

	public int getTotalNumberOfEvaluations() {
		return totalNumberOfEvaluations;
	}

	private synchronized void incrementPendingLineEvaluations() {
		pendingLineEvaluations++;
	}

	private synchronized void decrementPendingLineEvaluations() {
		pendingLineEvaluations--;
	}

	private synchronized void incrementPendingLqnsEvaluations() {
		pendingLqnsEvaluations++;
	}

	private synchronized void decrementPendingLqnsEvaluations() {
		pendingLqnsEvaluations--;
	}
	
	public synchronized boolean lineEvaluationsFinished(){		
		return pendingLineEvaluations == 0;
	}
	
	public synchronized boolean lqnsEvaluationsFinished(){
		return pendingLqnsEvaluations == 0;
	}

	public void exit() {
		handler.terminateLine();
		executor.shutdown();
	}
	
	public void logResults(){
		logger.info(",Model Name, LQNS solution Time, LINE solution Time");
		for(Path p:lineEvaluationTimes.keySet()){
			logger.info(","+p.getFileName()+","+lqnsEvaluationTimes.get(p.getFileName())+","+lineEvaluationTimes.get(p.getFileName()));
		}
	}

}
