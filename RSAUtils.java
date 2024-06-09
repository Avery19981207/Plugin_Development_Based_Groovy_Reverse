
import java.io.FileOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;

public class RSAUtils {
    private PrivateKey privateKey = null;
    private PublicKey publicKey = null;

    public static KeyPair generateKeyPair(){
        try{
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();
            return keyPair;
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }
}
