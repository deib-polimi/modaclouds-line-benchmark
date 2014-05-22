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

import it.polimi.modaclouds.qos.linebenchmark.lqn.LqnModelType;
import it.polimi.modaclouds.qos.linebenchmark.lqn.ObjectFactory;
import it.polimi.modaclouds.qos.linebenchmark.lqn.ProcessorType;
import it.polimi.modaclouds.qos.linebenchmark.solver.EvaluationServer;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.JAXBIntrospector;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.log4j.PropertyConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

	public static final String LINE_SOLVER = "LINE";
	public static final String LQNS_SOLVER = "LQNS";
	public static Path LINE_PROP_FILE = Paths.get("line.properties");
	private static Path MODEL_FOLDER = Paths.get("models");
	private static final Logger logger = LoggerFactory.getLogger(Main.class);	

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		ClassLoader loader = Thread.currentThread().getContextClassLoader();		
		PropertyConfigurator.configure(loader.getResourceAsStream("log4j.properties"));
		try {
			LINE_PROP_FILE = Paths.get(Main.class.getResource("/LINE.properties").toURI());
		} catch (URISyntaxException e) {			// 
			logger.error("error in retreiving line property file",e);
		}

		logger.info("Starting the tool..");
		File modelFolderFile = MODEL_FOLDER.toFile();

		//load the configuration for the evaluation
		Properties prop = new Properties();		
		InputStream input = null;		
		try {
			input = loader.getResourceAsStream("Evaluation.properties");
			prop.load(input);
		} catch (IOException e) {
			logger.error("Error in reading the properties file",e);
		} finally {
			if(input!=null)
				try {
					input.close();
				} catch (IOException e) {
					logger.error("error in closing the prperties file",e);
				}
		}

		if (!modelFolderFile.exists() && modelFolderFile.isDirectory()) {
			logger.error("Model folder missing");
			return;
		}

		//randomize models when needed
		if(Boolean.valueOf(prop.getProperty("random"))){			
			try {
				createRandomizedModel(prop);
			} catch (NoModelException e) {
				logger.error("No model have been specified in the \"models\" folder");
			}
		}

		File[] modelFiles = modelFolderFile.listFiles(new FilenameFilter() {

			@Override
			public boolean accept(File arg0, String arg1) {
				return arg1.endsWith(".xml") && !arg1.endsWith("_res.xml");
			}
		});
		logger.info("Launching Solvers");
		logger.info("LINE prop file: " + LINE_PROP_FILE.toAbsolutePath());
		EvaluationServer server = new EvaluationServer(true);
		logger.info("Starting LQNS evaluations");
		for (File f : modelFiles) {
			server.evaluateModel(f.toPath(), LQNS_SOLVER);
		}

		try {
			while (!server.lqnsEvaluationsFinished())			
				Thread.sleep(1 * 1000);			
			logger.info("Pending LQNS evaluations: "+server.getPendingLqnsEvaluations());
		} catch (InterruptedException e) {
			logger.error("error in waiting for evaluations",e);
		}

		logger.info("Starting LINE evaluations");
		for (File f : modelFiles) {
			server.evaluateModel(f.toPath(), LINE_SOLVER);
		}

		try {
			while (!server.lineEvaluationsFinished())
				Thread.sleep(1 * 1000);
			logger.info("Reamining LINE evaluations"+server.getPendingLineEvaluations());			
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		server.exit();
		server.logResults();
		logger.info("Terminated");

		return;
	}

	private static void createRandomizedModel(Properties props) throws NoModelException{

		File modelFolderFile = MODEL_FOLDER.toFile();
		int numberOfCopies = Integer.parseInt(props.getProperty("numberOfEval"));
		File[] modelFiles = modelFolderFile.listFiles(new FilenameFilter() {

			@Override
			public boolean accept(File arg0, String arg1) {
				return arg1.endsWith(".xml") && !arg1.endsWith("_res.xml");
			}
		});

		if(modelFiles.length==0)
			throw new NoModelException();

		JAXBContext jaxbContext;
		LqnModelType sourceModel = null;
		Marshaller marshaller = null;
		try {
			jaxbContext = JAXBContext.newInstance(LqnModelType.class);
			Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
			marshaller = jaxbContext.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			sourceModel = (LqnModelType) JAXBIntrospector.getValue(unmarshaller.unmarshal(modelFiles[0]));

		} catch (JAXBException e) {
			logger.error("Error in unmarshalling the model "+modelFiles[0].getName(),e);
		}

		//read the list of processors to randomize
		Set<String> processorNames = new HashSet<>();
		for(String s:props.getProperty("processorNames").split(","))
			processorNames.add(s);
		//read the name of the user scenario processor  
		String userScenarioProcessorName = props.getProperty("userScenarioProcessorName"); 

		logger.info("Generating "+numberOfCopies+" random models from: "+modelFiles[0].getName());
		int maxReplica = Integer.parseInt(props.getProperty("maxReplica"));
		int maxUsers = Integer.parseInt(props.getProperty("maxUsers"));
		double maxSpeedFactor= Double.parseDouble(props.getProperty("maxReplica"));
		for(int i=0;i<numberOfCopies;i++){
			//clone the model - might not be necessary
			//LqnModelType newModel = (LqnModelType) sourceModel.clone();
			File newModelFile = new File(modelFiles[0].getAbsolutePath().replaceFirst(".xml", "_"+i+".xml"));
			String logmessage = "Model File"+newModelFile.getName()+" ";
			for(ProcessorType proc:sourceModel.getProcessor()){
				//randomize processor replicas and speeds
				if(processorNames.contains(proc.getName())){
					//new random number from 1 to maxReplica
					int newReplica =  (int) Math.round(Math.random()*(maxReplica-1)+1);
					double newSpeedFactor = Math.random()*(maxSpeedFactor-1)+1;
					proc.setMultiplicity(""+newReplica);
					proc.setSpeedFactor(""+round(newSpeedFactor,2));
					logmessage += "Processor: "+proc.getName()+ " replicas: "+newReplica+ " speedFactor: "+newSpeedFactor;
				} 
				//change number of users
				else if(proc.getName().equals(userScenarioProcessorName)){
					int newNumberOfUsers =(int) Math.round(Math.random()*(maxUsers-1)+1); 
					proc.setMultiplicity(""+newNumberOfUsers);
					logmessage += "Processor: "+proc.getName()+" numberOfUsers: "+newNumberOfUsers;							
				}				
			}
			logger.trace(logmessage);
			try {
				ObjectFactory objectFactory = new ObjectFactory();
				JAXBElement<LqnModelType> je =  objectFactory.createLqnModel(sourceModel);
				marshaller.marshal(je, newModelFile);
			} catch (JAXBException e) {
				logger.error("error in marshalling file: "+newModelFile.getName(),e);
			}

		}


	}

	public static double round(double value, int places) {
		if (places < 0) throw new IllegalArgumentException();

		BigDecimal bd = new BigDecimal(value);
		bd = bd.setScale(places, RoundingMode.HALF_UP);
		return bd.doubleValue();
	}

}
