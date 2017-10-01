package nl.hsac.fitnesse.fixture.util.selenium.driverfactory;

import nl.hsac.fitnesse.fixture.slim.StopTestException;
import nl.hsac.fitnesse.fixture.util.selenium.SeleniumHelper;
import nl.hsac.fitnesse.fixture.util.selenium.by.BestMatchBy;
import org.openqa.selenium.WebDriver;

/**
 * Helps create and destroy selenium drivers wrapped in helpers.
 */
public class DriverManager {
    /** Default time in seconds the wait web driver waits unit throwing TimeOutException. */
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;
    private DriverFactory factory;
    private int defaultTimeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
    private SeleniumHelper helper;

    public DriverManager() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> closeDriver()));
    }

    public void setFactory(DriverFactory factory) {
        this.factory = factory;
    }

    public DriverFactory getFactory() {
        return factory;
    }

    public void closeDriver() {
        setSeleniumHelper(null);
    }

    public SeleniumHelper getSeleniumHelper() {
        if (helper == null) {
            DriverFactory currentFactory = getFactory();
            if (currentFactory == null) {
                throw new StopTestException("Cannot use Selenium before configuring how to start a driver (for instance using SeleniumDriverSetup)");
            } else {
                WebDriver driver = currentFactory.createDriver();
                SeleniumHelper newHelper = createHelper(driver);
                newHelper.setWebDriver(driver, getDefaultTimeoutSeconds());
                setSeleniumHelper(newHelper);
            }
        }
        return helper;
    }

    public void setSeleniumHelper(SeleniumHelper helper) {
        if (this.helper != null) {
            this.helper.close();
        }
        this.helper = helper;
    }

    protected SeleniumHelper createHelper(WebDriver driver) {
        // set default 'Best Function'
        BestMatchBy.setBestFunction(BestMatchBy::selectBestElement);
        return new SeleniumHelper();
    }

    public int getDefaultTimeoutSeconds() {
        return defaultTimeoutSeconds;
    }

    public void setDefaultTimeoutSeconds(int defaultTimeoutSeconds) {
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
    }
}
