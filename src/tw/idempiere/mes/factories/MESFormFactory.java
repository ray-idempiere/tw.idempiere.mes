package tw.idempiere.mes.factories;

import org.adempiere.webui.factory.IFormFactory;
import org.adempiere.webui.panel.ADForm;
//import tw.idempiere.mes.form.WFPanelManufacturing;
import tw.idempiere.mes.form.WProductionSchedule;

public class MESFormFactory implements IFormFactory {

	public MESFormFactory() {
	}

	@Override
	public ADForm newFormInstance(String formName) {
		/*
		 * if (formName.equals("tw.idempiere.mes.form.WFPanelManufacturing")) {
		 * return new WFPanelManufacturing();
		 * } else
		 */ if (formName.equals("tw.idempiere.mes.form.WProductionSchedule")) {
			return new WProductionSchedule();
		}
		return null;
	}

}
