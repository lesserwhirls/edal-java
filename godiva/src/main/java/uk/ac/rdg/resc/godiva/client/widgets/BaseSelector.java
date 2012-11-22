package uk.ac.rdg.resc.godiva.client.widgets;

import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;

/**
 * Base class for selection widgets. Handles adding a label to a
 * {@link HorizontalPanel} and setting the label style. Any other widget can
 * then be added
 * 
 * @author Guy Griffiths
 * 
 */
public class BaseSelector extends HorizontalPanel {
    protected Label label;

    public BaseSelector(String title) {
        super();

        label = new Label(title + ":");
        label.setStylePrimaryName("labelStyle");
        label.setWordWrap(true);
        add(label);
    }

    @Override
    public void setTitle(String title) {
        label.setText(title + ":");
    }
}
