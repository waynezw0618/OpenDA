/* MOD_V2.0 
* Copyright (c) 2012 OpenDA Association 
* All rights reserved.
* 
* This file is part of OpenDA. 
* 
* OpenDA is free software: you can redistribute it and/or modify 
* it under the terms of the GNU Lesser General Public License as 
* published by the Free Software Foundation, either version 3 of 
* the License, or (at your option) any later version. 
* 
* OpenDA is distributed in the hope that it will be useful, 
* but WITHOUT ANY WARRANTY; without even the implied warranty of 
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
* GNU Lesser General Public License for more details. 
* 
* You should have received a copy of the GNU Lesser General Public License
* along with OpenDA.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.openda.model_RainfallRunoffZhang;

import org.openda.exchange.AbstractDataObject;
import org.openda.exchange.DoubleExchangeItem;
import org.openda.interfaces.IExchangeItem;

import java.io.*;

/**
 * Allows reading of time variables from matlab readable ASCII file and
 * transferring these to OpenDA ExchangeItems. 
 * <p>
 * This code can be copied and
 * adapted to create wrappers for other blackbox models coded in matlab. Code to
 * be modified is indicated by comments starting with "// --". The input file
 * that is read by this wrapper has following format: 
 * <p>
 * 	key = value; % Some optional comment. <br>
 *  ...
 * <p>
 * White spaces are optional as is the comment. Comment lines and empty lines 
 * are skipped. keys that are not specified in this wrapper to be read are skipped. 
 * <p>
 * Note: Time specifications are the only exchange items which are not stored in 
 * time series but in conventional IExchangeItems. 
 * 
 * @author Beatrice Marti, hydrosolutions ltd.
 *
 */
public class TimesWrapper extends AbstractDataObject {

	// Class specific values
	File workingDir;
	String fileName = null;

	// Cache values to be read.
	// -- Add variables for initial States to be read here.
	private double currentTimeCache = 0.0;
	private double simulationTimeStepCache = 0.0;
	private double finalTimeCache = 0.0;

	/**
	 * Initialize the DataObject. Reads the content of a file (fileName) in
	 * directory (workingDir) with given arguments.
	 * 
	 * @param workingDir
	 *            Working directory
	 * @param arguments
	 *            The name of the file containing the data (relative to the
	 *            working directory), and additional arguments (may be null zero-length)
	 */
	@Override
	public void initialize(File workingDir, String[] arguments) {
		
		this.workingDir = workingDir;
		this.fileName = arguments[0];
		
		try {
			ReadNameListFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void finish() {
		// Updates time configuration file.
		double currentTime = exchangeItems.get("currentTime").getValuesAsDoubles()[0];
		double simulationTimeStep = exchangeItems.get("simulationTimeStep").getValuesAsDoubles()[0];
		double finalTime = exchangeItems.get("finalTime").getValuesAsDoubles()[0];
		
		//write to file
		System.out.println("TimesWrapper.finish(): writing to " +this.workingDir+"/"+this.fileName);
		File outputFile = new File(this.workingDir,this.fileName);
		try{
			if(outputFile.isFile()){
				outputFile.delete();
			}
		}catch (Exception e) {
			System.out.println("TimesWrapper.finish(): trouble removing file "+ fileName);
		}
		try {
			FileWriter writer = new FileWriter(outputFile);
			BufferedWriter out = new BufferedWriter(writer);

			out.write("currentTime = " + currentTime + ";\n");
			out.write("simulationTimeStep = " + simulationTimeStep + ";\n");
			out.write("finalTime = " + finalTime + ";\n");
			
			System.out.println("TimesWrapper.finish(): writes times [ " + currentTime + " , " + simulationTimeStep + " , " + finalTime + " ]");
			
			out.close();
			writer.close();

		} catch (Exception e) {
			throw new RuntimeException("TimesWrapper.finish(): Problem writing to file "+fileName+" :\n "+e.getMessage());
		}
		
	}
	

	/**
	 * Opens file fileName in directory workingDir, reads in values and stores
	 * them to an ExchangeItem.
	 * -- Adapt reading of the key-value pairs and writing to the ExchangeItem 
	 *     here below.
	 *
	 * @throws IOException
	 */
	private void ReadNameListFile() throws IOException {
		File namelist = new File(workingDir, fileName);
		if (!namelist.exists()) {
			throw new RuntimeException("TimesWrapper.ReadNameListFile(): settings file "
					+ namelist.getAbsolutePath() + " does not exist");
		}
		// Create nested reader.
		FileInputStream in = new FileInputStream(namelist);
		BufferedReader buff = new BufferedReader(new InputStreamReader(in));
		String line = ""; // Initialize line.
		boolean eof = false; // End of file cache.

		// While End of file is not reached yet do the following:
		while (!eof) {
			// Read line.
			line = buff.readLine();
			// System.out.println("line : " + line);
			// Test for end of file.
			if (line == null) {
				eof = true;
			}
			// If the end of the file is not reached yet split line and store
			// data.
			else {
				// Now parse the line.
				// Remove comments at end of line.
				if (line.indexOf("%") > 1) {
					String[] columns = line.split("%");
					line = columns[0];
				}
				if (line.startsWith("%")) {
					// If the lines starts with comment or meta data do nothing.
				} else if (line.contains(System.getProperty("line.separator"))) {
					// Skip empty lines.
				} else if (line.indexOf("=") > 0) {
					// Split key and value at "=".
					String[] columns = line.split("=");
					columns[0] = columns[0].trim(); // Removes white spaces in
													// the beginning or the end
													// of the string.
					columns[1] = columns[1].trim();
					// Remove the semicollon at the end of the string in columns[1].
					String[] temp = columns[1].split(";");
					columns[1] = temp[0];

					// Parse the values to the key caches in Java.
					// -- Add if-loops for variables to be read here.
					if (columns[0].equals("currentTime")) {
						currentTimeCache = Double.parseDouble(columns[1]);
					}
					if (columns[0].equals("simulationTimeStep")) {
						simulationTimeStepCache = Double.parseDouble(columns[1]);
					}
					
					if (columns[0].equals("finalTime")) {
						finalTimeCache = Double.parseDouble(columns[1]);
					}

				}
			}
		}
		// Close the writers.
		buff.close();
		in.close();

		// Parse the cached values to IExchangeItems.
		// -- Add commands for storing the read key-value pairs in the exchange items.
		IExchangeItem currentTimeExchangeItem = new DoubleExchangeItem(
				"currentTime", this.currentTimeCache);
		exchangeItems.put("currentTime", currentTimeExchangeItem);
        
        IExchangeItem finalTimeExchangeItem = new DoubleExchangeItem(
                "finalTime", this.finalTimeCache);
        exchangeItems.put("finalTime", finalTimeExchangeItem);
       
        IExchangeItem simulationTimeStepExchangeItem = new DoubleExchangeItem(
                "simulationTimeStep", this.simulationTimeStepCache);
        exchangeItems.put("simulationTimeStep", simulationTimeStepExchangeItem);
        
	}

}
