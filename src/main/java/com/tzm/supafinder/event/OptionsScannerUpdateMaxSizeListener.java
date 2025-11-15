package com.tzm.supafinder.event;

import com.tzm.supafinder.model.RegexScannerOptions;

import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;

import static com.tzm.supafinder.utils.Messages.getLocaleString;

public class OptionsScannerUpdateMaxSizeListener extends OptionsScannerUpdateListener {

    public OptionsScannerUpdateMaxSizeListener(RegexScannerOptions scannerOptions) {
        super(scannerOptions);
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        try {
            int newMaxSizeValue = Integer.parseInt(updatedStatusField.getText());
            if (newMaxSizeValue < 1)
                throw new NumberFormatException(getLocaleString("exception-sizeMustBeGreaterEqualThanOne"));

            scannerOptions.setConfigMaxResponseSize(newMaxSizeValue);
            SwingUtilities.invokeLater(() -> currentValueLabel.setText(String.valueOf(newMaxSizeValue)));
        } catch (NumberFormatException ignored) {
        }
    }
}
