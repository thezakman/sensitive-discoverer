package com.tzm.supafinder.event;

import com.tzm.supafinder.model.RegexScannerOptions;

import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;

import static com.tzm.supafinder.utils.Messages.getLocaleString;

public class OptionsScannerUpdateNumThreadsListener extends OptionsScannerUpdateListener {

    public OptionsScannerUpdateNumThreadsListener(RegexScannerOptions scannerOptions) {
        super(scannerOptions);
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        try {
            int newThreadNumber = Integer.parseInt(updatedStatusField.getText());
            if (newThreadNumber < 1 || newThreadNumber > 128)
                throw new NumberFormatException(getLocaleString("exception-numberNotInTheExpectedRange"));

            scannerOptions.setConfigNumberOfThreads(newThreadNumber);
            SwingUtilities.invokeLater(() -> currentValueLabel.setText(String.valueOf(newThreadNumber)));
        } catch (NumberFormatException ignored) {
        }
    }
}
