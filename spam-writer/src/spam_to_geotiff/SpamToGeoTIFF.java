package spam_to_geotiff;

import java.io.File;
import java.io.FilenameFilter;
import java.math.BigDecimal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.Driver;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconst;
import org.gdal.osr.osr;

import ru.smartflex.tools.dbf.DbfEngine;
import ru.smartflex.tools.dbf.DbfHeader;
import ru.smartflex.tools.dbf.DbfIterator;
import ru.smartflex.tools.dbf.DbfRecord;

public class SpamToGeoTIFF 
{

	// Constant values for 5 arc-minute grid
	public static int ncols = 4320;
	public static int nrows = 2160;
	public static int xmin = -180;
	public static int xmax = 180;
	public static int ymin = -90;
	public static int ymax = 90;
	public static double res = 0.083333;	
	public static double nodata = -1;
	
	// GeoTransform
	public static double[] geoTransform = {
			-180,
			res,
			0,
			90,
			0,
			-res			
	};
	
	// SPAM crop codes
	public static String[] cropCodes = {
		"WHEA",
		"RICE",
		"MAIZ",
		"BARL",
		"PMIL",
		"SMIL",
		"SORG",
		"OCER",
		"POTA",
		"SWPO",
		"YAMS",
		"CASS",
		"ORTS",
		"BEAN",
		"CHIC",
		"COWP",
		"PIGE",
		"LENT",
		"OPUL",
		"SOYB",
		"GROU",
		"CNUT",
		"OILP",
		"SUNF",
		"RAPE",
		"SESA",
		"OOIL",
		"SUGC",
		"SUGB",
		"COTT",
		"OFIB",
		"ACOF",
		"RCOF",
		"COCO",
		"TEAS",
		"TOBA",
		"BANA",
		"PLNT",
		"TROF",
		"TEMF",
		"VEGE",
		"REST",
		"VP_CROP",
		"VP_FOOD",
		"VP_NONF",
		"AREA_CR",
		"AREA_FO",
		"AREA_NF",
		"VP_CR_AR",
		"VP_FO_AR",
		"VP_NF_AR"
	};
	
	// SPAM technology code and name
	public static String[] techCodes = {
		"_A",	// All technologies together
		"_I",	// Irrigated
		"_H",	// Rainfed high input
		"_L",	// Rainfed low input
		"_S",	// Rainfed subsistence
		"_R"	// Rainfed
	};
	public static String[] techNames = {
		"_all",	
		"_irrigated",
		"_rainfed-high-input",
		"_rainfed-low-input",
		"_rainfed-subsistence",
		"_rainfed"
	};
	
	// Path to DBF
	public static String pathToWorkspace = "C:\\Users\\JKOO\\eclipse-workspace\\spam-writer\\";
	public static String pathToDBFs = pathToWorkspace + "dbf\\";
	public static String pathToOutputs = pathToWorkspace + "out\\";
	
	// Namespace for SPAM layers
	public static ArrayList<Object> getSpamLayerNames()
	{
		ArrayList<Object> spamLayerNames = new ArrayList<Object>();
		for (String cropCode: cropCodes)
			for (String techCode: techCodes)
				spamLayerNames.add(cropCode+techCode);
		return spamLayerNames;
	}
	
	// List of DBF file names
	public static String[] getDbfFileNames()
	{
		File dir = new File(pathToDBFs);
		FilenameFilter filter = new FilenameFilter() {
		    public boolean accept(File dir, String name) {
		        return (name.toUpperCase().contains(".DBF"));
		    }
		};
		String[] fileNames = dir.list(filter);
		return fileNames;
	}
	
	// Read DBF file 
	private static Map<Integer, BigDecimal> readMapLayer(String dbfFileName, String columnName) 
	{
		Map<Integer, BigDecimal> mapLayer = new HashMap<Integer, BigDecimal>();
		
		File dbfFile = new File(pathToDBFs+dbfFileName);
		DbfIterator dbfIterator = DbfEngine.getReader(dbfFile, null);
        
		while (dbfIterator.hasMoreRecords()) 
        {
    		DbfRecord dbfRecord = dbfIterator.nextRecord();
    		int cellID = dbfRecord.getInt("CELL5M");
    		BigDecimal cellValue = dbfRecord.getBigDecimal(columnName);
    		mapLayer.put(cellID, cellValue);
        }
		return mapLayer;
	}

	// Check the DBF header 
	private static boolean checkColumn(String dbfFileName, String columnName) 
	{
		File dbfFile = new File(pathToDBFs+dbfFileName);
		DbfHeader dbfHeader = DbfEngine.getHeader(dbfFile, null);
		boolean columnExist = dbfHeader.isColumnExisted(columnName);
		return columnExist;
	}
		
	// Write GeoTIFF
	public static void rasterWriter(String geoFileName, float[][] yxLayer)
    {

		// GDAL 
		gdal.AllRegister();
		
		Driver driver = gdal.GetDriverByName("GTiff");
        Band band = null;
        
        Dataset dataset = driver.Create(pathToOutputs+geoFileName, ncols, nrows, 1, gdalconst.GDT_Float32);
        dataset.SetProjection(osr.SRS_WKT_WGS84);
        dataset.SetGeoTransform(geoTransform);
        band = dataset.GetRasterBand(1);        

        for( int y=0; y<nrows; y++) 
        {
        	band.WriteRaster(0, y, ncols, 1, yxLayer[y]);
        }
        
        band.SetNoDataValue(nodata);
        gdal.Unlink(geoFileName);
        
    }
	
	
	// Main
	public static void main(String[] args) 
	{

		// Column names
		ArrayList<Object> spamLayerNames = getSpamLayerNames();
		
		// For each DBF file
		for (String dbfFileName: getDbfFileNames())
		{

			// Which layers are in it?
			for (Object spamLayerName: spamLayerNames)
			{
				String columnName = (String)spamLayerName;
				if (checkColumn(dbfFileName, columnName))
				{

					// File name
					String geoFileName = dbfFileName.split("\\.")[0].substring(0, dbfFileName.split("\\.")[0].length()-3)+"_"+columnName+".tif";
					System.out.println("> Creating "+geoFileName);
					
					// Read it, layer by layer
					Map<Integer, BigDecimal> mapLayer = readMapLayer(dbfFileName, columnName);
					
					// Shape it into XY layer
					float[][] yxLayer = new float[nrows][ncols];
					int cid = 0;
					for (int y=0; y<nrows; y++)
						for (int x=0; x<ncols; x++)
						{
							if (mapLayer.containsKey(cid))
							{
								yxLayer[y][x] = mapLayer.get(cid).floatValue();
							}
							else
							{
								yxLayer[y][x] = -1;
							}
							cid++;
						}
					
			        rasterWriter(geoFileName, yxLayer);
					
				}

			}
			
			
		} // for (String dbfFileName: getDbfFileNames())
		
	}

}
