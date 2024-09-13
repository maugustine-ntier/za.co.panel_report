package org.adempiere.webui.dashboard;

import java.io.File;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

import org.adempiere.webui.LayoutUtils;
import org.adempiere.webui.apps.AEnv;
import org.adempiere.webui.component.Label;
import org.adempiere.webui.component.ToolBarButton;
import org.adempiere.webui.desktop.DashboardController;
import org.adempiere.webui.report.HTMLExtension;
import org.adempiere.webui.theme.ThemeManager;
import org.adempiere.webui.util.IServerPushCallback;
import org.adempiere.webui.util.ServerPushTemplate;
import org.adempiere.webui.window.ZkReportViewerProvider;
import org.compiere.model.MColumn;
import org.compiere.model.MDashboardContent;
import org.compiere.model.MLookup;
import org.compiere.model.MLookupFactory;
import org.compiere.model.MLookupInfo;
import org.compiere.model.MMenu;
import org.compiere.model.MPInstance;
import org.compiere.model.MPInstancePara;
import org.compiere.model.MProcess;
import org.compiere.model.MProcessPara;
import org.compiere.model.MTable;
import org.compiere.print.MPrintFormat;
import org.compiere.print.ReportEngine;
import org.compiere.process.ProcessInfo;
import org.compiere.process.ServerProcessCtl;
import org.compiere.tools.FileUtil;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.Util;
import org.zkoss.util.media.AMedia;
import org.zkoss.zhtml.Text;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zul.Div;
import org.zkoss.zul.Iframe;
import org.zkoss.zul.Panel;
import org.zkoss.zul.Panelchildren;
import org.zkoss.zul.Separator;
import org.zkoss.zul.Toolbar;

public class DPReportPanel extends DashboardPanel implements EventListener<Event>{

	private static final long serialVersionUID = 1L;
	private final static CLogger logger = CLogger.getCLogger(DashboardController.class);
	int AD_Process_ID = -1;
	MDashboardContent dashboardContentCurr = null;
	String processParameters = null;
	Component parentComponent = null;
	String contextPath = "";
	Iframe iframe = null;
	Label rowCountLabel = null;
	IServerPushCallback callback;
	ServerPushTemplate template;


	public DPReportPanel() {
		super();

		//this.setSclass("reportpanel.box");
		//	this.setHeight("220px");
		try {
			initLayout();
		}
		catch (Exception e)
		{
			logger.log(Level.WARNING, "Failed to create dashboard content", e);
		}
	}






	private void initLayout() throws Exception {
		Panel panel = new Panel();
		//panel.setClass("fav-tree-panel");
		this.appendChild(panel);

		MDashboardContent [] mDashBoardContents = MDashboardContent.getForSession(true,0,0);
		for (MDashboardContent dashboardContent: mDashBoardContents) {
			dashboardContentCurr = dashboardContent;
			AD_Process_ID = dashboardContent.getAD_Process_ID();
			if(AD_Process_ID > 0)
			{
				boolean systemAccess = false;
				MProcess process = MProcess.get(Env.getCtx(), AD_Process_ID);
				String accessLevel = process.getAccessLevel();
				if (   MTable.ACCESSLEVEL_All.equals(accessLevel)
						|| MTable.ACCESSLEVEL_SystemOnly.equals(accessLevel)
						|| MTable.ACCESSLEVEL_SystemPlusClient.equals(accessLevel)) {
					systemAccess = true;
				}
				int thisClientId = Env.getAD_Client_ID(Env.getCtx());
				if((thisClientId == 0 && systemAccess) || thisClientId != 0) {
					String sql = "SELECT AD_Menu_ID FROM AD_Menu WHERE AD_Process_ID=?";
					int AD_Menu_ID = DB.getSQLValueEx(null, sql, AD_Process_ID);
					ToolBarButton btn = new ToolBarButton();
					MMenu menu = new MMenu(Env.getCtx(), AD_Menu_ID, null);					
					btn.setAttribute("AD_Menu_ID", AD_Menu_ID);
					btn.addEventListener(Events.ON_CLICK, this);					

					if (dashboardContent.isEmbedReportContent()) 
					{
						//addDrillAcrossEventListener(AD_Process_ID, parentComponent); martin
						processParameters = dashboardContent.getProcessParameters();
						parentComponent = null;  // Martin
						contextPath = "";
						ReportData2 reportData = generateReport(AD_Process_ID, dashboardContent.getAD_PrintFormat_ID(), processParameters, parentComponent, contextPath);

						Div layout = new Div();
						layout.setHeight("100%");
						layout.setStyle("display: flex;flex-direction: column;");
						Panelchildren pc = new Panelchildren();
						pc.appendChild(layout);
						panel.appendChild(pc);
						//components.add(layout); Martin
						iframe = new Iframe();
						iframe.setSclass("dashboard-report-iframe");
						iframe.setStyle("flex-grow: 1;");
						iframe.setContent(reportData.getContent());
						if(iframe.getContent() != null) {
							layout.appendChild(iframe);
						} else {
							layout.appendChild(createFillMandatoryLabel(dashboardContent));
						}

						Toolbar toolbar = new Toolbar();
						LayoutUtils.addSclass("dashboard-report-toolbar", toolbar);
						layout.appendChild(toolbar);
						btn.setLabel(Msg.getMsg(Env.getCtx(), "OpenRunDialog"));
						toolbar.appendChild(btn);

						if(iframe.getContent() != null && reportData.getRowCount() >= 0) {
							btn = new ToolBarButton();
							btn.setAttribute("AD_Process_ID", AD_Process_ID);
							btn.setAttribute("ProcessParameters", processParameters);
							btn.setAttribute("AD_PrintFormat_ID", dashboardContent.getAD_PrintFormat_ID());
							btn.addEventListener(Events.ON_CLICK, this);
							btn.setLabel(Msg.getMsg(Env.getCtx(), "ViewReportInNewTab"));
							toolbar.appendChild(new Separator("vertical"));
							toolbar.appendChild(btn);
						}
						btn = new ToolBarButton();
						if (ThemeManager.isUseFontIconForImage()) {
							btn.setIconSclass("z-icon-Refresh");
							btn.setSclass("trash-toolbarbutton");
						} else {
							btn.setImage(ThemeManager.getThemeResource("images/Refresh16.png"));
						}

						toolbar.appendChild(btn);	

						rowCountLabel = new Label(Msg.getMsg(Env.getCtx(), "RowCount", new Object[] {reportData.getRowCount()}));
						if(reportData.getRowCount() >= 0) {
							LayoutUtils.addSclass("rowcount-label", rowCountLabel);
							toolbar.appendChild(rowCountLabel);
						}

						btn.addEventListener(Events.ON_CLICK, e -> {
							ReportData2 refreshedData = generateReport(AD_Process_ID, dashboardContent.getAD_PrintFormat_ID(), processParameters, parentComponent, contextPath);
							iframe.setContent(refreshedData.getContent());
							if(refreshedData.getRowCount() >= 0) {
								rowCountLabel.setValue(Msg.getMsg(Env.getCtx(), "RowCount", new Object[] {refreshedData.getRowCount()}));
							}
						});		
						
					}
					else
					{
						btn.setLabel(menu.getName());
						// components.add(btn);  Martin
					}
				}
			}
		}

	}

	@Override
	public void refresh(ServerPushTemplate template)  {
		//	performanceData = WPAPanel.loadGoal();
		//usually, this should be call in non UI/Event listener thread (i.e Executions.getCurrent() should be null)
		try {
		//	refreshedData = generateReport(AD_Process_ID, dashboardContentCurr.getAD_PrintFormat_ID(), processParameters, parentComponent, contextPath);

		//	iframe.setContent(refreshedData.getContent());
		//	if(refreshedData.getRowCount() >= 0) {
		//		rowCountLabel.setValue(Msg.getMsg(Env.getCtx(), "RowCount", new Object[] {refreshedData.getRowCount()}));
		//	}
			this.template = new ServerPushTemplate(AEnv.getDesktop());
			this.callback = new IServerPushCallback() {
				@Override
				public void updateUI() {
					//label.setText(labeltext);
					try {
						ReportData2 refreshedData;
						refreshedData = generateReport(AD_Process_ID, dashboardContentCurr.getAD_PrintFormat_ID(), processParameters, parentComponent, contextPath);

						iframe.setContent(refreshedData.getContent());
						if(refreshedData.getRowCount() >= 0) {
							rowCountLabel.setValue(Msg.getMsg(Env.getCtx(), "RowCount", new Object[] {refreshedData.getRowCount()}));
						}
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			};
			template.executeAsync(callback);
		//	if (Executions.getCurrent() != null) {
		//		updateUI();			
		//	} else {
		//		template.executeAsync(this);
		//	}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	@Override
	public boolean isPooling() {
		return true;
	}

	@Override
	public void onEvent(Event event) throws Exception {
		// TODO Auto-generated method stub

	}


	/**
	 * Run report
	 * @param AD_Process_ID
	 * @param AD_PrintFormat_ID
	 * @param parameters Report parameters
	 * @return {@link ReportEngine}
	 */
	private ReportEngine runReport(int AD_Process_ID, int AD_PrintFormat_ID, String parameters) {
		MProcess process = MProcess.get(Env.getCtx(), AD_Process_ID);
		if (!process.isReport() || process.getAD_ReportView_ID() == 0) {
			throw new IllegalArgumentException("Not a Report AD_Process_ID=" + process.getAD_Process_ID()
			+ " - " + process.getName());
		}
		//	Process
		MPInstance pInstance = new MPInstance(Env.getCtx(), AD_Process_ID, 0, 0, null);
		if(AD_PrintFormat_ID > 0) {
			pInstance.setAD_PrintFormat_ID(AD_PrintFormat_ID);
		}
		pInstance.setIsProcessing(true);
		pInstance.saveEx();
		try {
			if(!fillParameter(pInstance, parameters)) {
				return null;
			}
			//
			ProcessInfo pi = new ProcessInfo (process.getName(), process.getAD_Process_ID(), 0, 0);
			pi.setAD_User_ID(Env.getAD_User_ID(Env.getCtx()));
			pi.setAD_Client_ID(Env.getAD_Client_ID(Env.getCtx()));
			pi.setAD_PInstance_ID(pInstance.getAD_PInstance_ID());		
			if (!process.processIt(pi, null) && pi.getClassName() != null) {
				throw new IllegalStateException("Process failed: (" + pi.getClassName() + ") " + pi.getSummary());
			}

			//	Report
			ReportEngine re = ReportEngine.get(Env.getCtx(), pi);
			if (re == null) {
				throw new IllegalStateException("Cannot create Report AD_Process_ID=" + process.getAD_Process_ID()
				+ " - " + process.getName());
			}
			return re;
		}
		finally {			
			pInstance.setIsProcessing(false);
			pInstance.saveEx();
		}

	}

	/**
	 * Generate report media (html)
	 * @param AD_Process_ID
	 * @param AD_PrintFormat_ID
	 * @param parameters
	 * @param component
	 * @param contextPath
	 * @return {@link AMedia}
	 * @throws Exception
	 */
	private ReportData2 generateReport(int AD_Process_ID, int AD_PrintFormat_ID, String parameters, Component component, String contextPath) throws Exception {
		MProcess process = MProcess.get(Env.getCtx(), AD_Process_ID);
		File file = null;
		if(process.getJasperReport() != null) {
			file = runJasperReport(process, parameters, AD_PrintFormat_ID);
			return new ReportData2(new AMedia(process.getName(), "html", "text/html", file, false), -1);
		}

		ReportEngine re = runReport(AD_Process_ID, AD_PrintFormat_ID, parameters);
		if(re == null) {
			return null;
		}
		file = FileUtil.createTempFile(re.getName(), ".html");		
		re.createHTML(file, false, AEnv.getLanguage(Env.getCtx()), new HTMLExtension(contextPath, "rp", 
				((component == null) ? null : component.getUuid()), String.valueOf(AD_Process_ID)));
		return new ReportData2(new AMedia(process.getName(), "html", "text/html", file, false), re.getPrintData() != null ? re.getPrintData().getRowCount(false) : 0);
	}

	private File runJasperReport(MProcess process, String parameters, int AD_PrintFormat_ID) {
		MPInstance pInstance = new MPInstance(Env.getCtx(), process.getAD_Process_ID(), 0, 0, null);
		pInstance.setIsProcessing(true);
		pInstance.saveEx();
		try {
			if(!fillParameter(pInstance, parameters))
			{
				return null;
				//
			}

			ProcessInfo pi = new ProcessInfo (process.getName(), process.getAD_Process_ID(), 0, 0);
			pi.setExport(true);
			pi.setExportFileExtension("html");
			pi.setAD_User_ID(Env.getAD_User_ID(Env.getCtx()));
			pi.setAD_Client_ID(Env.getAD_Client_ID(Env.getCtx()));
			pi.setAD_PInstance_ID(pInstance.getAD_PInstance_ID());
			if(AD_PrintFormat_ID > 0) {
				MPrintFormat format = new MPrintFormat(Env.getCtx(), AD_PrintFormat_ID, null);
				pi.setTransientObject(format);
			}

			//	Report
			ServerProcessCtl.process(pi, null);

			return pi.getExportFile();
		}catch(Exception ex) {
			throw new IllegalStateException("Cannot create Report AD_Process_ID=" + process.getAD_Process_ID()
			+ " - " + process.getName());
		}
	}

	/**
	 * Run report and open in report viewer
	 * @param AD_Process_ID
	 * @param AD_PrintFormat_ID
	 * @param parameters
	 */
	protected void openReportInViewer(int AD_Process_ID, int AD_PrintFormat_ID, String parameters) {
		ReportEngine re = runReport(AD_Process_ID, AD_PrintFormat_ID, parameters);
		new ZkReportViewerProvider().openViewer(re);
	}

	/**
	 * Fill Parameters
	 * @param pInstance
	 * @param parameters
	 * @return true if the parameters were filled successfully 
	 */
	private boolean fillParameter(MPInstance pInstance, String parameters) {	
		MProcessPara[] processParams = pInstance.getProcessParameters();
		if (parameters != null && parameters.trim().length() > 0) {
			Map<String, String> paramMap = MDashboardContent.parseProcessParameters(parameters);
			for (int pi = 0; pi < processParams.length; pi++)
			{
				MPInstancePara iPara = new MPInstancePara (pInstance, processParams[pi].getSeqNo());
				iPara.setParameterName(processParams[pi].getColumnName());
				iPara.setInfo(processParams[pi].getName());

				MProcessPara sPara = processParams[pi];

				String variable = paramMap.get(iPara.getParameterName());

				if (Util.isEmpty(variable, true)) {
					if(sPara.isMandatory()) {
						return false;	// empty mandatory parameter
					} else {
						continue;
					}
				}

				boolean isTo = false;

				for (String paramValue : variable.split(";")) {

					//				Value - Constant/Variable
					Object value = paramValue;
					if (paramValue == null
							|| (paramValue != null && paramValue.length() == 0)) {
						value = null;
					} else if (paramValue.startsWith(MColumn.VIRTUAL_UI_COLUMN_PREFIX)) {
						String sql = paramValue.substring(5);
						sql = Env.parseContext(Env.getCtx(), 0, sql, false, false);	//	replace variables
						if (!Util.isEmpty(sql)) {
							PreparedStatement stmt = null;
							ResultSet rs = null;
							try {
								stmt = DB.prepareStatement(sql, null);
								rs = stmt.executeQuery();
								if (rs.next()) {
									if (   DisplayType.isNumeric(iPara.getDisplayType()) 
											|| DisplayType.isID(iPara.getDisplayType())) {
										value = rs.getBigDecimal(1);
									} else if (DisplayType.isDate(iPara.getDisplayType())) {
										value = rs.getTimestamp(1);
									} else {
										value = rs.getString(1);
									}
								} else {
									if (logger.isLoggable(Level.INFO)) {
										logger.log(Level.INFO, "(" + iPara.getParameterName() + ") - no Result: " + sql);
									}
								}
							}
							catch (SQLException e) {
								logger.log(Level.WARNING, "(" + iPara.getParameterName() + ") " + sql, e);
							}
							finally{
								DB.close(rs, stmt);
								rs = null;
								stmt = null;
							}
						}
					}	//	SQL Statement
					else if (paramValue.indexOf('@') != -1)	//	we have a variable
					{
						value = Env.parseContext(Env.getCtx(), 0, paramValue, false, false);
					}	//	@variable@

					//	No Value
					if (value == null)
					{
						if(sPara.isMandatory()) {
							return false;	// empty mandatory parameter
						}
						else {
							continue;
						}
					}
					if( DisplayType.isText(iPara.getDisplayType())
							&& Util.isEmpty(String.valueOf(value))) {
						if (logger.isLoggable(Level.FINE)) {
							logger.fine(iPara.getParameterName() + " - empty string");
						}
						break;
					}

					//	Convert to Type				
					if (DisplayType.isNumeric(iPara.getDisplayType()))
					{
						BigDecimal bd = null;
						if (value instanceof BigDecimal) {
							bd = (BigDecimal)value;
						} else if (value instanceof Integer) {
							bd = new BigDecimal (((Integer)value).intValue());
						} else {
							bd = new BigDecimal (value.toString());
						}
						DecimalFormat decimalFormat = DisplayType.getNumberFormat(iPara.getDisplayType());
						String info = decimalFormat.format(iPara.getP_Number());
						if (isTo) {
							iPara.setP_Number_To(bd);
							iPara.setInfo_To(info);
						}
						else {
							iPara.setP_Number(bd);
							iPara.setInfo(info);
						}
					}
					else if (iPara.getDisplayType() == DisplayType.Search || iPara.getDisplayType() == DisplayType.Table || iPara.getDisplayType() == DisplayType.TableDir) {
						int id = new BigDecimal (value.toString()).intValue();
						if (isTo) {
							iPara.setP_Number_To(new BigDecimal (value.toString()));
							iPara.setInfo_To(getDisplay(pInstance, iPara, id));
						}
						else {
							iPara.setP_Number(new BigDecimal (value.toString()));
							iPara.setInfo(getDisplay(pInstance, iPara, id));
						}
					}
					else if (DisplayType.isDate(iPara.getDisplayType()))
					{
						Timestamp ts = null;
						if (value instanceof Timestamp) {
							ts = (Timestamp)value;
						} else {
							ts = Timestamp.valueOf(value.toString());
						}
						SimpleDateFormat dateFormat = DisplayType.getDateFormat(iPara.getDisplayType());
						String info = dateFormat.format(ts);
						if (isTo) {
							iPara.setP_Date_To(ts);
							iPara.setInfo_To(info);
						}
						else {
							iPara.setP_Date(ts);
							iPara.setInfo(info);
						}
					}
					else
					{
						if (isTo) {
							iPara.setP_String_To(value.toString());
							iPara.setInfo_To(value.toString());
						}
						else if(DisplayType.isChosenMultipleSelection(iPara.getDisplayType())) {
							iPara.setP_String(value.toString());
							iPara.setInfo(getMultiSelectionDisplay(pInstance, iPara, value.toString()));
						}
						else {
							iPara.setP_String(value.toString());
							iPara.setInfo(value.toString());
						}
					}
					iPara.saveEx();

					isTo = true;
				}
			}
		}
		else {
			for(MProcessPara processPara : processParams) {
				if(processPara.isMandatory()) {
					return false;	// empty mandatory parameter
				}
			}
		}
		return true;
	}

	private String getMultiSelectionDisplay(MPInstance i, MPInstancePara ip, String values) {
		String returnValue = "";
		String[] splittedValues = values.split("[,]");
		for(String value : splittedValues) {
			if(!Util.isEmpty(returnValue)) {
				returnValue += ", ";
			}
			returnValue += getDisplay(i, ip, DisplayType.ChosenMultipleSelectionList == ip.getDisplayType() ? value : Integer.parseInt(value));
		}
		return returnValue;
	}

	private String getDisplay(MPInstance i, MPInstancePara ip, Object value) {
		try {
			MProcessPara pp = MProcess.get(i.getAD_Process_ID()).getParameter(ip.getParameterName());

			if (pp != null) {
				MLookupInfo mli = MLookupFactory.getLookupInfo(Env.getCtx(), 0, 0, pp.getAD_Reference_ID(), Env.getLanguage(Env.getCtx()), pp.getColumnName(), pp.getAD_Reference_Value_ID(), false, "");

				PreparedStatement pstmt = null;
				ResultSet rs = null;
				StringBuilder name = new StringBuilder("");
				try
				{
					pstmt = DB.prepareStatement(mli.QueryDirect, null);
					if(value instanceof Integer) {
						pstmt.setInt(1, (Integer)value);
					} else {
						pstmt.setString(1, Objects.toString(value, ""));
					}

					rs = pstmt.executeQuery();
					if (rs.next()) {
						name.append(rs.getString(3));
						boolean isActive = rs.getString(4).equals("Y");
						if (!isActive) {
							name.insert(0, MLookup.INACTIVE_S).append(MLookup.INACTIVE_E);
						}

						if (rs.next()) {
							logger.log(Level.SEVERE, "Error while displaying parameter for embedded report - Not unique (first returned) for SQL=" + mli.QueryDirect);
						}
					}
				}
				catch (Exception e) {
					logger.log(Level.SEVERE, "Error while displaying parameter for embedded report - " + mli.KeyColumn + ": SQL=" + mli.QueryDirect + " : " + e);
				}
				finally {
					DB.close(rs, pstmt);
					rs = null;
					pstmt = null;
				}

				return name.toString();
			}
		}
		catch (Exception e) {
			logger.log(Level.WARNING, "Failed to retrieve data to display for embedded report " + MProcess.get(i.getAD_Process_ID()).getName() + " : " + ip.getParameterName(), e);
		}

		return Objects.toString(value, "");
	}

	/**
	 * Create Fill Mandatory Process Parameters error label for the reports in dashboard
	 * @return Div
	 */
	private Div createFillMandatoryLabel(MDashboardContent dc) {
		Div wrapper = new Div();
		wrapper.setSclass("fill-mandatory-process-para-wrapper");

		Div msgText = new Div();
		msgText.appendChild(new Text(Msg.getMsg(Env.getCtx(), "FillMandatoryParametersDashboard", new Object[] {dc.getEmptyMandatoryProcessPara()})));
		LayoutUtils.addSclass("fill-mandatory-process-para-text", msgText);
		wrapper.appendChild(msgText);
		return wrapper;
	}

	/**
	 * Holds information about the report: Report Content, Row Count
	 */
	public class ReportData2 {
		/** Report content */
		private AMedia content;
		/** Report Row Count */
		private int rowCount = 0;

		/**
		 * Constructor
		 * @param content
		 * @param rowCount
		 */
		public ReportData2(AMedia content, int rowCount) {
			this.content = content;
			this.rowCount = rowCount;
		}

		/**
		 * Get report content
		 * @return AMedia content
		 */
		public AMedia getContent() {
			return content;
		}

		/**
		 * Get report row count (function rows not included)
		 * @return int row count
		 */
		public int getRowCount() {
			return rowCount;
		}
	}


}
