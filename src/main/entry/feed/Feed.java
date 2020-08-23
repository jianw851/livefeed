package feed;

import java.io.IOException;
import java.security.GeneralSecurityException;

public interface Feed {
    public void run() throws IOException, RuntimeException, GeneralSecurityException;
}
