package za.ntier.models;

import java.sql.ResultSet;
import java.util.Properties;

import org.compiere.model.I_AD_Process;
import org.compiere.model.MDashboardContent;

public class MDashboardContent_New extends MDashboardContent implements I_PA_DashboardContent {

	private static final long serialVersionUID = 1L;

	public MDashboardContent_New(Properties ctx, String PA_DashboardContent_UU, String trxName) {
		super(ctx, PA_DashboardContent_UU, trxName);
		// TODO Auto-generated constructor stub
	}

	public MDashboardContent_New(Properties ctx, int PA_DashboardContent_ID, String trxName) {
		super(ctx, PA_DashboardContent_ID, trxName);
		// TODO Auto-generated constructor stub
	}

	public MDashboardContent_New(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void setAD_Process_Report_ID(int AD_Process_Report_ID) {
		set_Value (COLUMNNAME_AD_Process_Report_ID, AD_Process_Report_ID);
		
	}

	@Override
	public int getAD_Process_Report_ID() {
		return get_ValueAsInt(COLUMNNAME_AD_Process_Report_ID);
	}

	@Override
	public I_AD_Process getAD_Process_Report() throws RuntimeException {
		return null;
	}

	

}
