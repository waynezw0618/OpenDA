package org.openda.model_dflowfm;

import junit.framework.TestCase;
import org.openda.interfaces.IExchangeItem;
import org.openda.utils.OpenDaTestSupport;
import org.springframework.util.Assert;

import java.io.File;

/**
 * Created by prevel on 30-Nov-15.
 */
public class Md1dFileTest extends TestCase
{
	OpenDaTestSupport testData = null;
	private File testMd1dFileDir;
	private String md1dFileNameOriginal = "Model.md1d";
	private String md1dFileNameGenerated = "Model_generated.md1d";

	protected void setUp()
	{
		testData = new OpenDaTestSupport(BcFileTest.class, "model_dflowfm_blackbox");
		testMd1dFileDir = new File(testData.getTestRunDataDir(), "Md1dFile");
	}

	protected void tearDown(){}

	private class MockMd1dFile extends Md1dFile
	{
		public void alterExchangeItems(double factor)
		{
			for(IExchangeItem exchangeItem : exchangeItems.values())
			{
				if(!exchangeItem.getId().equals(Md1dTimeInfoExchangeItem.PropertyId.TimeStep.name()))
				{
					double[] values = exchangeItem.getValuesAsDoubles();
					values[0] = values[0] * factor;
					exchangeItem.setValuesAsDoubles(values);
				}
			}
		}

		public void alterTimeStep()
		{
			for(IExchangeItem exchangeItem : exchangeItems.values())
			{
				if(exchangeItem.getId().equals(Md1dTimeInfoExchangeItem.PropertyId.TimeStep.name()))
				{
					exchangeItem.setValuesAsDoubles(new double[]{0.0});
				}
			}
		}
	}

	public void testMd1dFileUpdatesCategoriesCorrectly()
	{
		// Step 1: Read original test file
		MockMd1dFile md1dFile = new MockMd1dFile();
		md1dFile.initialize(testMd1dFileDir, new String[]{md1dFileNameOriginal, md1dFileNameGenerated});

		// Step 2: Alter ExchangeItem Values
		md1dFile.alterExchangeItems(0.5);

		//Step 3: Write test file
		md1dFile.finish();

		// Step 4: Compare written file to expected results
		Assert.isTrue(FileComparer.Compare(new File(testMd1dFileDir, md1dFileNameGenerated),
				new File(testMd1dFileDir, "Model_TimeValuesHalved.md1d")));
	}

	public void testMd1dFileThrowsIfUpdatingTimeStep()
	{
		// Step 1: Read original test file
		MockMd1dFile md1dFile = new MockMd1dFile();
		md1dFile.initialize(testMd1dFileDir, new String[]{md1dFileNameOriginal, md1dFileNameGenerated});

		// Step 2: Alter ExchangeItem Values
		Exception expectedException = null;
		try
		{
			md1dFile.alterTimeStep();
		}
		catch(Exception ex)
		{
			expectedException = ex;
		}
		Assert.notNull(expectedException);
	}

	public void testMd1dFileGeneratesExpectedFile()
	{
		// Step 1: Read original test file
		Md1dFile md1dFile = new Md1dFile();
		md1dFile.initialize(testMd1dFileDir, new String[]{md1dFileNameOriginal, md1dFileNameGenerated});

		//Step 2: Write test file
		md1dFile.finish();

		// Step 3: Compare written file to expected results
		Assert.isTrue(FileComparer.Compare(new File(testMd1dFileDir, md1dFileNameOriginal),
				new File(testMd1dFileDir, md1dFileNameGenerated)));

	}

	public void testMd1dFileInitialiseThrowsExceptionForInvalidFile_MissingData()
	{
		Exception expectedException = null;
		Md1dFile md1dFile = new Md1dFile();
		try
		{
			md1dFile.initialize(testMd1dFileDir, new String[]{"Model_BadFormat1.md1d", ""});
		}
		catch(Exception ex)
		{
			expectedException = ex;
		}
		Assert.notNull(expectedException);
	}

	public void testMd1dFileInitialiseThrowsExceptionForInvalidFile_NonDoubleData()
	{
		Exception expectedException = null;
		Md1dFile md1dFile = new Md1dFile();
		try
		{
			md1dFile.initialize(testMd1dFileDir, new String[]{"Model_BadFormat2.md1d", ""});
		}
		catch(Exception ex)
		{
			expectedException = ex;
		}
		Assert.notNull(expectedException);
	}
}