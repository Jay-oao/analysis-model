package edu.hm.hafner.analysis.registry;

import edu.hm.hafner.analysis.IssueParser;
import edu.hm.hafner.analysis.parser.JcReportParser;

/**
 * A descriptor for the JcReport compiler.
 *
 * @author Lorenz Munsch
 */
class JcreportDescriptor extends ParserDescriptor {
    private static final String ID = "jc-report";
    private static final String NAME = "JCReport";

    JcreportDescriptor() {
        super(ID, NAME);
    }

    @Override
    public IssueParser create(final Option... options) {
        return new JcReportParser();
    }
}
