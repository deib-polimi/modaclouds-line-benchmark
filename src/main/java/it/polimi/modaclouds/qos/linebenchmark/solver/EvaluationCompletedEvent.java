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

import java.awt.event.ActionEvent;
import java.nio.file.Path;

public class EvaluationCompletedEvent extends ActionEvent {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Path modelPath;
	private String solverName;
	private long evaluationTime;

	public EvaluationCompletedEvent(Object source, int id, String command) {
		super(source, id, command);
		// TODO Auto-generated constructor stub
	}

	public long getEvaluationTime() {
		return evaluationTime;
	}

	public Path getModelPath() {
		return modelPath;
	}

	public String getSolverName() {
		return solverName;
	}

	public void setEvaluationTime(long evaluationTime) {
		this.evaluationTime = evaluationTime;
	}

	public void setModelPath(Path modelPath) {
		this.modelPath = modelPath;
	}

	public void setSolverName(String solverName) {
		this.solverName = solverName;
	}

}
