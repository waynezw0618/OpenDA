/* MOD_V1.0
 * Copyright (c) 2013 OpenDA Association
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
package org.openda.model_openfoam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.joda.time.DateTime;
import org.openda.exchange.timeseries.TimeSeries;
import org.openda.exchange.timeseries.TimeSeriesSet;
import org.openda.exchange.timeseries.TimeUtils;
import org.openda.interfaces.IDataObject;
import org.openda.interfaces.IExchangeItem;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;


/**
 *
 * Read an OpenFOAM dictionary file that contains lines of the format:
 *
 * keyword value;\\#exchangeItemID
 *
 * and
 *
 * keyword (value1 value2 ...);\\#exchangeItemID
 *
 * For each value found in a line indicated by \\#exchangeItemID a TimeSeries object is created.
 * All other lines are kept as-is.
 *
 * A referenceDate (ISO 8601 format) can be specified as a second argument. When a referenceDate is specified,
 * the value for exchangeItemId's "oda:startTime" and "oda:endTime" is converted from seconds to modified Julian days.
 *
 *  @author Werner Kramer (VORtech)
 */

@SuppressWarnings("unused")
public class DictionaryTimeSeriesDataObject implements IDataObject{


    private static final Logger logger = LoggerFactory.getLogger(DictionaryTimeSeriesDataObject.class);
    private String fileName = null;
	private static final String keyWordPrefix =";//#";
	private static final String multiplexId ="@";
	//private HashMap<String,IExchangeItem> items = new LinkedHashMap<>();
	private HashMap<String,Integer> multiplexColumn = new LinkedHashMap<>();
	private ArrayList<String> fileContent = new ArrayList<>();

	private double referenceMjd = 0.0;
	private boolean convertTime = false;

	static private final String WITH_DELIMITER = "((?<=%1$s)|(?=%1$s))";

    private TimeSeriesSet timeSeriesSet = null;
    private String idSeparator = ".";

    //private String arrayBrackets = "\\(\\)";
	//private String arrayDelimiter = " ";


    /**
     * Reads OpenFoam results generated by the sample utility.
     *
     * @param workingDir the working directory.
     * @param arguments list of other arguments:
     * <ol>
     * <li>The name of the file containing the data
     *      for this IoObject (relative to the working directory).</li>
     * <li>Optional, a referenceDate in ISO 8601 notatation, e.g
     *      for this IoObject (relative to the working directory).</li>
     *
     * </ol>
     */
	public void initialize(File workingDir, String[] arguments) {

		if ( arguments.length == 0 ) {
			throw new RuntimeException("No arguments are given when initializing.");
		} else if (arguments.length == 2) {
			Date date = new DateTime( arguments[1] ).toDate();
			this.referenceMjd = TimeUtils.date2Mjd(date);
			this.convertTime = true;
		}
		this.fileName = arguments[0];
        this.timeSeriesSet = new TimeSeriesSet();

        logger.info("DictionaryDataObject: filename = " + this.fileName);
		File inputFile;
		// check file
		try{
			inputFile = new File(workingDir,fileName);
			if(!inputFile.isFile()){
				throw new IOException("DictionaryDataObject: Can not find file " +  inputFile);
			}
			this.fileName = inputFile.getCanonicalPath();
		}catch (Exception e) {
			throw new RuntimeException("DictionaryDataObject: trouble opening file " + this.fileName);
		}
		//read file and parse to hash
		try {
			Scanner scanner = new Scanner(inputFile);
			scanner.useLocale(Locale.US);

			//FileInputStream in = new FileInputStream(inputFile);
			//BufferedReader buff = new BufferedReader(new InputStreamReader(in));

			String line;
			while (scanner.hasNext()) {

				line = scanner.nextLine();
				//logger.info("Line: "+ line);
				fileContent.add(line);
				int locationIndex = line.indexOf(keyWordPrefix);
//				logger.info("Found prefix: "+ locationID);
				//Scanner lineScanner = new Scanner(line);
				if (locationIndex > 0) {
					logger.debug("Line: "+ line);
					String valueString="";
					String key = line.substring(locationIndex + keyWordPrefix.length() );
					logger.debug("Key " + key);
					line = line.substring(0,locationIndex);

					String[] parts = line.split(String.format(WITH_DELIMITER, "\\(|\\)|\\s"));
					Vector<Double> values = new Vector<>();
					Vector<Integer> column = new Vector<>();
					for ( int index=0; index < parts.length ;index++) {
					//for ( String part : parts) {
						if (!parts[index].isEmpty()) {
							try {
								Double value = Double.parseDouble(parts[index]);
								values.add(value);
								column.add(index);
								logger.debug("Found part " + parts[index]);
							} catch (NumberFormatException e) {
								//logger.info("Skipping " + parts[index]);
							}
						}
					}
					if ( values.size() == 1 ) {
                        double[] time = new double[1];
                        time[0] = this.referenceMjd;
						double[] value = new double[1];
                        value[0] = values.firstElement();
                        TimeSeries series = new TimeSeries( time, value);
                        String location = "1";
                        series.setQuantity(key);
                        series.setLocation(location);
                        String id = series.getId();
                        timeSeriesSet.add(series);
						multiplexColumn.put(id,column.firstElement());
					} else {
                        double[] time = new double[1];
                        time[0] = this.referenceMjd;
                        for (int index=0 ; index < values.size() ; index++  ) {
                            double[] value = new double[1];
                            value[0] = values.elementAt(index);
                            TimeSeries series = new TimeSeries( time, value);
                            String location = Integer.toString(index+1);
                            series.setQuantity(key);
                            series.setLocation(location);
                            String id = series.getId();
                            timeSeriesSet.add(series);
							multiplexColumn.put(id,column.elementAt(index));
						}
					}
				}
			}
			scanner.close();
		} catch (IOException e) {
			throw new RuntimeException("Problem reading from file " + fileName+" : "+e.getClass());
		}
    }

    /** {@inheritDoc}
     */
    public IExchangeItem getDataObjectExchangeItem(String exchangeItemID) {

        String[] parts = Pattern.compile(idSeparator, Pattern.LITERAL).split(exchangeItemID);
        if (parts.length != 2) {
            throw new RuntimeException("Invalid exchangeItemID " + exchangeItemID );
        }
        String location = parts[0];
        String quantity = parts[1];


        // Get the single time series based on location and quantity
        TimeSeriesSet myTimeSeriesSet = this.timeSeriesSet.getOnQuantity(quantity)
            .getOnLocation(location);
        Iterator<TimeSeries> iterator = myTimeSeriesSet.iterator();
        if (!iterator.hasNext()) {
            throw new RuntimeException("No time series found for " + exchangeItemID);
        }
        TimeSeries timeSeries = iterator.next();
        if (iterator.hasNext()) {
            throw new RuntimeException("Time series is not uniquely defined for  " + exchangeItemID);
        }
        return timeSeries;


    }

	/** {@inheritDoc}
	 */
    public String [] getExchangeItemIDs() {
        String [] result = new String[this.timeSeriesSet.size()];
        Set<String> quantities = this.timeSeriesSet.getQuantities();
        int idx=0;
        for (String quantity: quantities) {
            Set<String> locations = this.timeSeriesSet.getOnQuantity(quantity).getLocations();
            for (String location: locations) {
                String id = location + idSeparator + quantity;
                result[idx]= id;
                idx++;
            }
        }
        return result;
    }

	/** {@inheritDoc}
	 */
    public String [] getExchangeItemIDs(IExchangeItem.Role role) {
        return getExchangeItemIDs();
    }



    /** {@inheritDoc}
	 */
	public void finish() {
        	//write to file
		File outputFile = new File(fileName);
		try{
			if(outputFile.isFile()){
				if ( ! outputFile.delete() ) throw new RuntimeException("Cannot delete " + outputFile);
			}
		}catch (Exception e) {
			logger.error("DictionaryDataObject: trouble removing file " + this.fileName +" :\n" + e.getMessage());
		}
		try {
			FileWriter writer = new FileWriter(outputFile);
			BufferedWriter out = new BufferedWriter(writer);
            for (String line: fileContent){
				int locationIndex = line.indexOf(keyWordPrefix);
//				logger.info("Found prefix: "+ locationID);
				//Scanner lineScanner = new Scanner(line);
                    if (locationIndex > 0) {
					logger.debug("Line: " + line);
					String valueString = "";
					String key = line.substring(locationIndex + keyWordPrefix.length());
					logger.debug("Key " + key);
					line = line.substring(0, locationIndex);
					String[] parts = line.split(String.format(WITH_DELIMITER, "\\(|\\)|\\s"));
					int nr = 1;
                    while (multiplexColumn.containsKey(nr + this.idSeparator + key)) {
                        String id = nr + this.idSeparator + key;
                        int index = multiplexColumn.get(id);
                        double[] paramValue = this.getDataObjectExchangeItem(id).getValuesAsDoubles();
                        parts[index] = Double.toString(paramValue[0]);
						nr++;
					}
					StringBuilder builder = new StringBuilder();
					for(String part : parts) {
						builder.append(part);
					}
					String outputLine = builder.toString() + keyWordPrefix + key + "\n";
					logger.debug("Write line" + outputLine);
					out.write(outputLine);
				}
                else {
                    //Write Line
                    out.write(line + "\n");
                }
            }
			out.close();
		} catch (Exception e) {
			throw new RuntimeException("DictionaryDataObject: Problem writing to file " + this.fileName+" :\n" + e.getMessage());
		}
    }

    /**
     * @return Reference to the time series set
     */
    public TimeSeriesSet getTimeSeriesSet() {
        return this.timeSeriesSet;
    }

    /**
     * @param set
     *           The TimeSeriesSet to set in this IoObject
     */
    public void setTimeSeriesSet(TimeSeriesSet set) {
        this.timeSeriesSet = set;
    }


}
