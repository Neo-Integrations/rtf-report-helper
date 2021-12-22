package org.mule.rtfreport;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataConsolidateFunction;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPivotTable;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

public class ExcelRTFReportExporter {

	final private Logger _logger = LoggerFactory.getLogger(ExcelRTFReportExporter.class);
	private final static String REPORT_TEMPLATE_NAME = "RTF-Usage-Analysis.xlsx";
	private final static String TEMP_FILE = "/assets/tmp/";
	private final static List<String> RTF_REPORT_HEADERS = Lists.newArrayList("Node", "Namespace", "Name",
			"CPU Requests", "CPU Limits", "Memory Requests", "Memory Limits", "AGE", "Application Pod",
			"CPU Requests Value", "CPU Limits Value", "CPU Burst Value", "Memory Requests Value",
			"Memory Limits Value");

	private XSSFWorkbook workbook = null;

	/*
	 * Open the file from the classpath (src/main/resources)
	 */
	public ExcelRTFReportExporter() {
		ClassLoader classLoader = getClass().getClassLoader();
		File file = new File(classLoader.getResource("./assets/" + REPORT_TEMPLATE_NAME).getFile());
		try (InputStream inputStream = new FileInputStream(file)) {
			workbook = new XSSFWorkbook(inputStream);
		} catch (FileNotFoundException fnfe) {
			_logger.error("FileNotFoundException", fnfe);
		} catch (IOException e) {
			_logger.error("IOException", e);
		}
	}

	public String createNodeSheet(final List<Map<String, Object>> records, final String environment,
			final String appHome) throws IOException {

		if (workbook == null)
			throw new IllegalArgumentException("Workbook is null");

		try {

			XSSFSheet dataSheet = renameSheet("ENV", environment);
			XSSFSheet nodeSheet = renameSheet("ENV Nodes", environment + " Nodes");

			cleanSheet(dataSheet);
			createHeader(dataSheet, RTF_REPORT_HEADERS, 0);

			int index = 0;
			for (Map<String, Object> eachRecord : records) {
				createRecord(dataSheet, RTF_REPORT_HEADERS, eachRecord, (index + 1));
				index++;
			}
			createPivotTable(nodeSheet, dataSheet);
		} catch (Exception e) {
			_logger.error("Somthing went wrong", e);
			System.out.print(e.getMessage());
			e.printStackTrace();
		}

		return this.save(appHome);
		// return stream();
	}

	private void createPivotTable(final XSSFSheet nodeSheet, final XSSFSheet dataSheet) {

		int firstRow = dataSheet.getFirstRowNum();
		int lastRow = dataSheet.getLastRowNum();
		int firstCol = dataSheet.getRow(0).getFirstCellNum();
		int lastCol = dataSheet.getRow(0).getLastCellNum();

		CellReference topLeft = new CellReference(dataSheet.getSheetName(), firstRow, firstCol, true, true);
		CellReference botRight = new CellReference(dataSheet.getSheetName(), lastRow, lastCol - 1, true, true);
		AreaReference aref = new AreaReference(topLeft, botRight, SpreadsheetVersion.EXCEL2007);

		CellReference pos = new CellReference(0, 0);
		XSSFPivotTable pivotTable = nodeSheet.createPivotTable(aref, pos);

		pivotTable.addRowLabel(0);

		pivotTable.addColumnLabel(DataConsolidateFunction.COUNT, 1, "Pod Count");
		pivotTable.addColumnLabel(DataConsolidateFunction.SUM, 9, "CPU Requests");
		pivotTable.addColumnLabel(DataConsolidateFunction.SUM, 10, "CPU Limits");
		pivotTable.addColumnLabel(DataConsolidateFunction.SUM, 12, "Memory Requests");
		pivotTable.addColumnLabel(DataConsolidateFunction.SUM, 13, "Memory Limits");

	}

	private void cleanSheet(final XSSFSheet sheet) {
		Iterator<Row> rowIte = sheet.iterator();
		while (rowIte.hasNext()) {
			rowIte.next();
			rowIte.remove();
		}
	}

	private XSSFSheet renameSheet(final String oldSheetName, final String newSheetName) {
		XSSFSheet eachSheet = workbook.getSheet(oldSheetName);
		if (eachSheet != null) {
			workbook.setSheetName(workbook.getSheetIndex(eachSheet), newSheetName);
		}
		return workbook.getSheet(newSheetName);
	}

	private String save(final String appHome) throws IOException {
		File newFile = new File(appHome + TEMP_FILE + java.util.UUID.randomUUID() + ".xlsx");

		try (FileOutputStream writer = new FileOutputStream(newFile)) {
			workbook.write(writer);
		}

		return newFile.getAbsolutePath();
	}

	private void createHeader(final Sheet sheet, final List<String> headerKeys, final int recordIndex) {

		Row header = sheet.createRow(recordIndex);
		CellStyle headerStyle = createHeaderStyle(sheet);

		for (int index = 0; index < headerKeys.size(); index++) {
			sheet.setColumnWidth(index, 4000);
			this.createCell(header, headerKeys.get(index), index, headerStyle);
		}
	}

	private void createRecord(final Sheet sheet, final List<String> headerKeys, final Map<String, Object> node,
			final int recordIndex) {

		Row record = sheet.createRow(recordIndex);
		CellStyle recordStyle = createRecordStyle(sheet);

		for (int index = 0; index < headerKeys.size(); index++) {
			Object name = node.get(headerKeys.get(index));
			sheet.setColumnWidth(index, 4000);
			this.createCell(record, name, index, recordStyle);
		}
	}

	private Cell createCell(final Row row, final Object name, final int index, final CellStyle style) {

		Cell cell = row.createCell(index);
		if (name instanceof Integer)
			cell.setCellValue(new Double((int) name));
		else
			cell.setCellValue((String) name);

		cell.setCellStyle(style);

		return cell;
	}

	private CellStyle createHeaderStyle(final Sheet sheet) {

		CellStyle headerStyle = workbook.createCellStyle();
		headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
		headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		headerStyle.setFont(headerFont());
		return headerStyle;
	}

	private Font headerFont() {
		Font headerFont = workbook.createFont();
		headerFont.setBold(true);
		headerFont.setFontName("Arial");
		headerFont.setFontHeightInPoints((short) 14);
		headerFont.setColor(HSSFColor.HSSFColorPredefined.WHITE.getIndex());

		return headerFont;
	}

	private CellStyle createRecordStyle(final Sheet sheet) {
		CellStyle recordStyle = workbook.createCellStyle();
		recordStyle.setFillForegroundColor(IndexedColors.WHITE.getIndex());
		recordStyle.setFont(recordFont());
		return recordStyle;
	}

	private Font recordFont() {
		Font recordFont = workbook.createFont();
		recordFont.setBold(false);
		recordFont.setFontName("Arial");
		recordFont.setFontHeightInPoints((short) 12);
		recordFont.setColor(HSSFColor.HSSFColorPredefined.BLACK.getIndex());

		return recordFont;
	}

	public static void updateChartData(XSSFChart chart, XSSFSheet dataSheet) {
		
		int[] seriesColumns = new int[]{1,2,3};
		

		for (XDDFChartData chartData : chart.getChartSeries()) {
			for (int s = 0; s < chartData.getSeriesCount(); s++) {
				XDDFChartData.Series series = chartData.getSeries(s);
				
				XDDFDataSource category = series.getCategoryData();
				
				System.out.println(category);
				
				//XDDFCategoryDataSource category = XDDFDataSourcesFactory.fromStringCellRange(
				//	      dataSheet, new CellRangeAddress(firstDataRow, lastDataRow, categoryColumn, categoryColumn));
				//	     int seriesColumn = seriesColumns[s];
					     
			    //XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(
				//	      dataSheet, new CellRangeAddress(2, 4, 1, 1));
				//	     series.replaceData(category, values); 
				
			    //series.replaceData(category, values);

			}
		}
	}

	public static void main(String[] args) throws Exception {

		String filePath = "/Users/mhaque/Downloads/shell-workspace/rtf-report-helper-v1/src/main/resources/assets/RTF-Usage-Analysis.xlsx";
		java.util.Random random = new java.util.Random();
		XSSFWorkbook workbook = (XSSFWorkbook) WorkbookFactory.create(new FileInputStream(filePath));
		XSSFSheet sheet = workbook.getSheetAt(1);

		XSSFDrawing drawing = sheet.createDrawingPatriarch();
		XSSFChart chart = drawing.getCharts().get(0);
		updateChartData(chart, sheet);
		
		

		filePath = "ExcelWithChart.xlsx";
		FileOutputStream out = new FileOutputStream(filePath);
		workbook.write(out);
		out.close();
		workbook.close();

	}

}
