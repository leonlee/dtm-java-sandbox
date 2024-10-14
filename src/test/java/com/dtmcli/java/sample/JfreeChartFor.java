package com.dtmcli.java.sample;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.StandardChartTheme;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.labels.StandardXYItemLabelGenerator;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYSplineRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.RectangleEdge;

public class JfreeChartFor {
	// 参考文章 https://www.jianshu.com/p/6c4f3832c396
	/**
	 * 生成的图片存放地址
	 */
	public static String imagePath = "/Users/yuwenhao/Desktop";

	public static void main(String[] args) throws Exception {
		// 生成曲线图
		curve();
	}

	/**
	 * 生成曲线图
	 */
	@SuppressWarnings("deprecation")
	public static void curve() {
		// 如 果不使用Font,中文将显示不出来
		Font font = new Font("宋体", Font.BOLD, 25);
		// 数据
		XYSeries series = new XYSeries("2020年");
		series.add(01, 3542);
		series.add(02, 3692);
		series.add(03, 8542);
		series.add(04, 5742);

		XYSeries series1 = new XYSeries("2021年");
		series1.add(01, 1242);
		series1.add(02, 2612);
		series1.add(03, 1942);
		series1.add(04, 4612);

		XYSeriesCollection xySeriesCollection = new XYSeriesCollection();
		xySeriesCollection.addSeries(series);
		xySeriesCollection.addSeries(series1);

		StandardChartTheme standardChartTheme = new StandardChartTheme("CN"); // 创建主题样式
		standardChartTheme.setExtraLargeFont(font); // 设置标题字体
		standardChartTheme.setRegularFont(font); // 设置图例的字体
		standardChartTheme.setLargeFont(font); // 设置轴向的字体
		standardChartTheme.setChartBackgroundPaint(Color.WHITE);// 设置主题背景色
		ChartFactory.setChartTheme(standardChartTheme);// 应用主题样式

		// 定义图表对象 图表的标题-饼形图的数据集对象-是否显示图列-是否显示提示文-是否生成超链接
		JFreeChart chart = ChartFactory.createXYLineChart("并发量/吞吐", "并发量", "吞吐", xySeriesCollection,
				PlotOrientation.VERTICAL, true, true, false);
		XYPlot plot = (XYPlot) chart.getPlot(); // 获得图表显示对象

		plot.setOutlineVisible(false);// 是否显示外边框
		plot.setOutlinePaint(Color.WHITE);// 外边框颜色
		// plot.setOutlineStroke(new BasicStroke(2.f));// 外边框框线粗细
		plot.setBackgroundPaint(Color.WHITE);// 白色背景
		plot.setNoDataMessage("无图表数据");// 无数据提示
		plot.setNoDataMessageFont(font);// 提示字体
		plot.setNoDataMessagePaint(Color.RED);// 提示字体颜色

		// 图例
		LegendTitle legend = chart.getLegend();// 图例对象
		legend.setPosition(RectangleEdge.BOTTOM);// 图例位置 上、下、左、右
		legend.setVisible(true);// 是否显示图例
		legend.setBorder(BlockBorder.NONE);// 图例无边框
		legend.setItemFont(font);// 图例大小

		// 网格线
		plot.setDomainGridlinePaint(Color.BLUE);
		plot.setDomainGridlinesVisible(true);// 竖线
		plot.setRangeGridlinePaint(Color.BLACK);
		plot.setRangeGridlinesVisible(true);// 横线

		// 横坐标

		NumberAxis xAxis = (NumberAxis) plot.getDomainAxis();// 获得横坐标
		xAxis.setTickLabelFont(font);// 设置X轴字体
		xAxis.setLabelFont(font);// 轴标签值字体
		xAxis.setTickLabelFont(font);// 轴标签字体
		xAxis.setAxisLineStroke(new BasicStroke(2f)); // 设置轴线粗细
		xAxis.setAxisLinePaint(Color.BLACK);// 轴线颜色
		xAxis.setLowerMargin(0.03D);// 左侧边距
		xAxis.setUpperMargin(0.03D);// 右侧边距
		xAxis.setTickUnit(new NumberTickUnit(1D));// 间距1D

		// 纵坐标
		ValueAxis yAxis = plot.getRangeAxis();
		yAxis.setTickLabelFont(font);// 设置y轴字体
		yAxis.setLabelFont(font);// 设置X轴标签字体
		yAxis.setAxisLineStroke(new BasicStroke(1f)); // 设置y轴线粗细
		yAxis.setAxisLinePaint(Color.BLACK);// 轴线颜色
		yAxis.setUpperMargin(0.18D);// 上边距
		yAxis.setLowerMargin(0.1D);// 下边距

		// 标签
		XYSplineRenderer renderer = new XYSplineRenderer();
		renderer.setItemLabelGenerator(new StandardXYItemLabelGenerator());
		renderer.setBaseItemLabelsVisible(true); // 基本项标签显示
		renderer.setBaseShapesVisible(true);
		renderer.setShapesFilled(Boolean.TRUE); // 在数据点显示实心的小图标
		renderer.setShapesVisible(true); // 设置显示小图标
		renderer.setItemLabelFont(font);// 设置数字的字体大小
		renderer.setStroke(new BasicStroke(4f));
		plot.setRenderer(renderer);

		int width = 1800;
		int height = 800;
		File p = new File(imagePath);
		if (!p.exists()) {
			p.mkdirs();
		}
//        String imageName = System.currentTimeMillis() + "_曲线图" + ".jpeg";
		String imageName = "曲线图" + ".jpeg";
		File file = new File(p.getPath() + "/" + imageName);
		try {
			if (file.exists()) {
				file.delete();
			}
			ChartUtilities.saveChartAsJPEG(file, chart, width, height);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}