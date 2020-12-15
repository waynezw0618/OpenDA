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

package org.openda.model_openfoam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.joda.time.DateTime;

import org.openda.exchange.timeseries.TimeSeries;
import org.openda.exchange.timeseries.TimeSeriesSet;
import org.openda.exchange.timeseries.TimeUtils;
import org.openda.interfaces.IComposableDataObject;

import org.openda.interfaces.IExchangeItem;
import org.openda.interfaces.IExchangeItem.Role;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.Vector;


/**



import java.util.zip.GZIPOutputStream;

//import org.openda.exchange.QuantityInfo;
//import org.openda.interfaces.IQuantityInfo;


/**
 * IDataObject to read samples generated by the OpenFOAM sample utility.
 *
 *
 *

 # Probe 0 (16 10 0)
 # Probe 1 (18 10 0)
 #       Probe             0             1
 #        Time
 1       2.30025       2.46272
 2       1.01854       1.02659
 3        4.5103       4.71018
 4        6.9461       7.37628
 5       7.80594        8.3458
 6       7.73234       8.24088
 7       7.26365       7.68736
 8       6.86536       7.21654
 9       6.66162       6.95746
 10       6.58902       6.87036

 *
 *
 * @author Werner Kramer
 */
public class ProbeDataObject implements IComposableDataObject {


    private static final Logger logger = LoggerFactory.getLogger(ProbeDataObject.class);


    private static final String PROPERTY_PATHNAME = "pathName";

	private File workingDir;
    private File file;
    //private InputStream inputStream;
    //private OutputStream outputStream;
    //private boolean create = false;
	private static final String idSeparator= ".";
	private static final String locationSeparator= "#";
	//private Map headerFields = new HashMap();
    //private Map<String,Integer> unitsMap = new LinkedHashMap<>();
    //private int writePrecision = 6;
	private TimeSeriesSet timeSeriesSet = new TimeSeriesSet();
	private double referenceMjd;
	private static final double SECONDS_TO_DAYS = 1.0 / 24.0 / 60.0 / 60.0;
    private static final String OPENFOAM_TIME_DIR = "OPENFOAM_TIME_DIR";

    
    /**
     * Reads OpenFoam results generated by the sample utility.
     *
     * @param workingDir the working directory.
     * @param arguments list of other arguments:
     * <ol>
     * <li>The name of the file containing the data
     *      for this DataObject (relative to the working directory).</li>
     * </ol>
     */
    public void initialize(File workingDir, String[] arguments) {

        if ( arguments.length == 0 ) {
            throw new RuntimeException("No arguments are given when initializing.");
        } else if (arguments.length == 1) {
			throw new RuntimeException("Reference datetime is required as argument when initializing");
		}

        this.workingDir = workingDir;
        String filePath = arguments[0];
        if ( filePath.contains(OPENFOAM_TIME_DIR) ) {
            int start = filePath.indexOf(OPENFOAM_TIME_DIR)-1;
            File dir;
            if (start == -1 ) {
                dir = workingDir;
            } else {
                dir = new File(workingDir, filePath.substring(0,start) );
            }
            String latestTimeDir="0";
            File[] directoryItems = dir.listFiles();
            if (directoryItems != null) {
                double max = 0;
                for (File item : directoryItems )  {
                    if ( item.isFile()) continue;
                    try {
                        double time = Double.parseDouble(item.getName());
                        if ( time > max ) {
                            max = time;
                            latestTimeDir = item.getName();
                        }
                    } catch (NumberFormatException e) {
                        logger.trace("Directory cannnot be parsed as time:" + item.getName() );
                    }
                }
            } else {
                throw new RuntimeException("Directory does not exist: " + dir.getAbsolutePath());
            }
            filePath = filePath.replaceFirst(OPENFOAM_TIME_DIR,latestTimeDir);
        }


        this.file = new File(workingDir, filePath);
		Date date = new DateTime( arguments[1] ).toDate();
		this.referenceMjd = TimeUtils.date2Mjd(date);

		logger.debug(this.file.getName());
		logger.debug(this.file.getParentFile().getParentFile().getName());
		String location = this.file.getParentFile().getParentFile().getName(); //name of subdirectory two level up
		String quantity = this.file.getName(); // Unit : waterlevel_astro !!! Note different label


		if (this.file.exists()) {

			int lineNr = 0;
			int nrProbes = 0;
			Vector<Double> timeVector = new Vector<Double>(); // for temporary storage of values,
			Vector<Vector<Double>> valuesVector = new Vector<Vector<Double>>(nrProbes);
			List<String> positions = new ArrayList<>();
            try {
				Scanner scanner = new Scanner(this.file);
				scanner.useLocale(Locale.US);
				// parse header and count nr probes
				scanner.useDelimiter("\\s+|\\)");
				while (scanner.hasNext(Pattern.compile("#"))) {
					lineNr++;
					if (scanner.findInLine("# Probe") != null) {
						int probeNr = scanner.nextInt();
						// parse location
						double[] coordinates = new double[3];
						scanner.findInLine("\\(");
						if (probeNr == nrProbes) {
							for (int i = 0; i < coordinates.length; i++) {
								coordinates[i] = scanner.nextDouble();
							}
							String position = String.format(Locale.US,"( %f , %f, %f )", coordinates[0], coordinates[1], coordinates[2]);
                            logger.debug(position);
                            nrProbes++;
                            positions.add(position);
							scanner.nextLine();
						}
					} else {
						//scanner.nextLine();
						logger.debug(scanner.nextLine());
					}
				}
				// parse data rows

				// check if data row contains scalar or vector data
				boolean firstLine = true;
					while (scanner.hasNext()) {
						lineNr++;
						String line = scanner.nextLine();
						logger.debug("Line : " + line);
						Scanner lineScanner = new Scanner(line);
						lineScanner.useLocale(Locale.US);
						lineScanner.useDelimiter("\\s+\\(|\\)\\s+\\(|\\)|\\s+");
						try {
							timeVector.add(lineScanner.nextDouble());
							int nrFields = 0;
							while (lineScanner.hasNext()) {
								if (firstLine) valuesVector.add(new Vector<Double>());
								valuesVector.get(nrFields).add(lineScanner.nextDouble());
								nrFields++;
							}
							firstLine = false;
						}  catch (InputMismatchException e) {
							throw new RuntimeException("Invalid character '" + lineScanner.next() + "' in file "  + this.file + " at line: " + lineNr + "\n");
						}
						lineScanner.close();

					}
				scanner.close();
			} catch (InputMismatchException e) {
				throw new RuntimeException("Invalid entry in file " + this.file + " at line: " + lineNr + "\n");
			}
			catch (IOException e) {
				throw new RuntimeException("Exception in file " + this.file  + " at line: " + lineNr + "\n"  + e.getMessage());
			}
			int nrComponents = valuesVector.size() / nrProbes;
			logger.debug("File " + this.file + " contains vector data : " + nrComponents);


			// create time series
			double[] times = new double[timeVector.size()];
			double[] values = new double[timeVector.size()];
			double x = Double.NaN;
			double y = Double.NaN;
			String source = "probe";
			String unit     = "";
			Role role     = Role.Output;

			for (int i = 0; i < times.length; i++) {
				times[i] = timeVector.get(i) * SECONDS_TO_DAYS + this.referenceMjd;
			}
			int v = 0;
			for (Vector<Double> valuesVec : valuesVector ) {
				for (int i = 0; i < times.length; i++) {
					values[i] = valuesVec.get(i);
				}
				int component = v%nrComponents;
				int probeNr   = v/nrComponents;
				String myQuantity = (nrComponents == 1) ? quantity : quantity + ( component+1);
				TimeSeries timeSeries = new TimeSeries(times, values, x, y, source, myQuantity, unit, location + locationSeparator + probeNr, role);
					//timeSeries.setProperty(PROPERTY_ANALTIME, analTime);
					//timeSeries.setProperty(PROPERTY_TIMEZONE, timeZone);
					//timeSeries.setDescription(description);
				this.timeSeriesSet.add(timeSeries);
				v++;
			}
		}
		else {
			throw new RuntimeException("File " + this.file + " does not exist");
		}

    }

    /** {@inheritDoc}
     */
    public String[] getExchangeItemIDs() {
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
    public String[] getExchangeItemIDs(Role role) {
        // only Output from the model or observer is implemented
        if (role == Role.InOut) {
           return getExchangeItemIDs();
        }
        else {
            return null;
        }
    }

    /** {@inheritDoc}
     */
    public IExchangeItem getDataObjectExchangeItem(String exchangeItemID) {

		// FIXME
		// String[] parts = exchangeItemID.split(this.idSeparator);
		String[] parts = Pattern.compile(idSeparator, Pattern.LITERAL).split(exchangeItemID);
		if (parts.length != 2) {
			throw new RuntimeException("Invalid exchangeItemID " + exchangeItemID );
		}
		String location = parts[0];
		String quantity = parts[1];

		// Get a single time series based on location and quantity
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
    public void addExchangeItem(IExchangeItem item) {
        if (item instanceof org.openda.exchange.timeseries.TimeSeries) {
            TimeSeries timeSeries = (TimeSeries) item;
            timeSeries.setProperty(PROPERTY_PATHNAME,file.getAbsolutePath());
            timeSeriesSet.add((TimeSeries) item);
        } else {
            logger.warn("This dataobject cannot add exchange item of type " + item.getClass() + "." );
        }
    }

    public void finish() {
    }

}
