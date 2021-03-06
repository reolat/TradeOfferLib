package com.ryanspeets.tradeoffer;

import com.google.gson.Gson;
import org.apache.http.Consts;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.crypto.Cipher;
import javax.xml.bind.DatatypeConverter;
import java.awt.*;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.URI;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.*;
import java.util.List;

public class TradeUser {
    protected DefaultHttpClient httpClient;

    public TradeUser()
    {
        httpClient = new DefaultHttpClient();
    }

    public void addCookie(Cookie cookie)
    {
        httpClient.getCookieStore().addCookie(cookie);
    }

    /**
     * Makes a request to the specified URL as this user.
     *
     * Inspired/Borrowed from SteamBot
     * @param url The URL to fetch
     * @param method Either "GET" or "POST" TODO: Should make this an enum later
     * @param data The POST parameters to pass along
     * @param ajax Whether or not this is an AJAX request
     * @return The contents of the URL as a string.
     * @throws IOException
     */
    public String fetch(String url, String method, List<NameValuePair> data, boolean ajax) throws IOException
    {
        HttpResponse response = request(url, method, data, ajax);
        java.util.Scanner s = new java.util.Scanner(response.getEntity().getContent(), "UTF-8").useDelimiter("\\A");
        return  s.hasNext() ? s.next() : "";
    }

    /**
     * Makes a request to the specified URL as this user.
     *
     * Inspired/Borrowed from SteamBot
     * @param url The URL to fetch
     * @param method Either "GET" or "POST" TODO: Should make this an enum later
     * @param data The POST parameters to pass along
     * @param ajax Whether or not this is an AJAX request
     * @return
     * @throws IOException
     */
    public HttpResponse request(String url, String method, List<NameValuePair> data, boolean ajax) throws IOException
    {
        HttpRequest request;
        if(method.equals("POST"))
        {
            request = new HttpPost(url);
        } else //(method.equals("POST"))
        {
            request = new HttpGet(url);
        }

        request.setHeader("Accept", "text/javascript, text/html, application/xml, text/xml, */*");
        request.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        request.setHeader("Host", "steamcommunity.com");
        request.setHeader("Referer", "http://steamcommunity.com/tradeoffer/1");

        if (ajax)
        {
            request.setHeader("X-Requested-With", "XMLHttpRequest");
            request.setHeader("X-Prototype-Version", "1.7");
        }

        if(data != null && !method.equals("GET"))
        {
            ((HttpPost)request).setEntity(new UrlEncodedFormEntity(data, Consts.UTF_8));
        }
        return httpClient.execute((HttpUriRequest)request);
    }

    public class GetRsaKey
    {
        public boolean success;
        public String publickey_mod;
        public String publickey_exp;
        public String timestamp;
    }

    public class SteamResult
    {
        public boolean success;
        public String message;
        public boolean captcha_needed;
        public String captcha_gid;
        public boolean emailauth_needed;
        public String emailsteamid;
        public HashMap<String, String> transfer_parameters;
        String transfer_url;
    }

    public Trade getTrade(int id) throws Exception {
        return new Trade(this, id, null);
    }

    public Trade newTrade(SteamID partner) throws Exception {
        Trade trade = new Trade(this, 0, partner);
        return trade;
    }

    public void addCookie(String name, String value, boolean secure)
    {
        BasicClientCookie cookie = new BasicClientCookie(name, value);
        cookie.setVersion(0);
        cookie.setDomain("steamcommunity.com");
        cookie.setPath("/");
        cookie.setSecure(secure);
        this.addCookie(cookie);
    }

    /**
     * Log into the specified account
     *
     * Inspired/Borrowed from SteamBot
     * @param username
     * @param password
     * @return
     * @throws IOException
     */
    public boolean login(String username, String password) throws Exception
    {
        Scanner scanner = new Scanner(System.in);
        List<NameValuePair> data = new ArrayList<NameValuePair>();
        data.add(new BasicNameValuePair("username", username));
        String response = fetch("https://steamcommunity.com/login/getrsakey", "POST", data, false);
        Gson gson = new Gson();
        GetRsaKey rsaJSON = gson.fromJson(response, GetRsaKey.class);


        // Validate
        if (!rsaJSON.success)
        {
            return false;
        }


        BigInteger m = new BigInteger(rsaJSON.publickey_mod, 16);
        BigInteger e = new BigInteger(rsaJSON.publickey_exp, 16);
        RSAPublicKeySpec spec = new RSAPublicKeySpec(m, e);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        PublicKey key = keyFactory.generatePublic(spec);

        Cipher rsa = Cipher.getInstance("RSA");
        rsa.init(Cipher.ENCRYPT_MODE, key);

        byte[] encodedPassword = rsa.doFinal(password.getBytes("ASCII"));
        String encryptedBase64Password = DatatypeConverter.printBase64Binary(encodedPassword);


        SteamResult loginJson = null;
        String steamGuardText = "";
        String steamGuardId   = "";
        do
        {
            System.out.println("SteamWeb: Logging In...");

            boolean captcha = loginJson != null && loginJson.captcha_needed;
            boolean steamGuard = loginJson != null && loginJson.emailauth_needed;

            String time = rsaJSON.timestamp;
            String capGID = loginJson == null ? null : loginJson.captcha_gid;

            data = new ArrayList<NameValuePair>();
            data.add(new BasicNameValuePair("password", encryptedBase64Password));
            data.add(new BasicNameValuePair("username", username));

            // Captcha
            String capText = "";
            if (captcha)
            {
                System.out.println("SteamWeb: Captcha is needed.");

                if(Desktop.isDesktopSupported())
                {
                    Desktop.getDesktop().browse(new URI("https://steamcommunity.com/public/captcha.php?gid=" + loginJson.captcha_gid));
                } else {
                    System.out.println("https://steamcommunity.com/public/captcha.php?gid=" + loginJson.captcha_gid);
                }
                System.out.println("SteamWeb: Type the captcha:");
                capText = scanner.nextLine();
            }

            data.add(new BasicNameValuePair("captchagid", captcha ? capGID : "-1"));
            data.add(new BasicNameValuePair("captcha_text", captcha ? capText : ""));
            // Captcha end

            // SteamGuard
            if (steamGuard)
            {
                System.out.println("SteamWeb: SteamGuard is needed.");
                System.out.println("SteamWeb: Type the code:");
                steamGuardText = scanner.nextLine();
                steamGuardId   = loginJson.emailsteamid;
            }

            data.add(new BasicNameValuePair("emailauth", steamGuardText));
            data.add(new BasicNameValuePair("emailsteamid", steamGuardId));
            // SteamGuard end

            data.add(new BasicNameValuePair("rsatimestamp", time));

            HttpResponse webResponse = request("https://steamcommunity.com/login/dologin/", "POST", data, false);

            loginJson = gson.fromJson(new InputStreamReader(webResponse.getEntity().getContent()), SteamResult.class);

        } while (loginJson.captcha_needed || loginJson.emailauth_needed);


        if (loginJson.success)
        {
            data = new ArrayList<NameValuePair>();
            for (Map.Entry<String, String> stringStringEntry : loginJson.transfer_parameters.entrySet()) {
                Map.Entry pairs = (Map.Entry) stringStringEntry;
                data.add(new BasicNameValuePair((String) pairs.getKey(), (String) pairs.getValue()));
            }
            fetch("https://steamcommunity.com/login/transfer", "POST", data, false );

            return true;
        }
        else
        {
            System.out.println("SteamWeb Error: " + loginJson.message);
            return false;
        }
    }

    public TradeOffer[] getIncomingTradeOffers() throws IOException
    {
        Document document = Jsoup.parse(fetch("http://steamcommunity.com/my/tradeoffers","GET",null,false));
        Elements tradeOfferElements = document.getElementsByClass("tradeoffer");

        ArrayList<TradeOffer> offers = new ArrayList<TradeOffer>();

        for(Element tradeOfferElement : tradeOfferElements)
        {

            int id = Integer.parseInt(tradeOfferElement.id().substring(13)); // strip off tradeofferid_
            boolean active = tradeOfferElement.getElementsByClass("tradeoffer_items_ctn").get(0).hasClass("active");
            TradeOffer tradeOffer = new TradeOffer(id, active, this);
            offers.add(tradeOffer);
        }

        return offers.toArray(new TradeOffer[offers.size()]);
    }
}
