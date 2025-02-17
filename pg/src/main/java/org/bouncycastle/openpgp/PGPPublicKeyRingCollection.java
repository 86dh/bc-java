package org.bouncycastle.openpgp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.bouncycastle.bcpg.BCPGOutputStream;
import org.bouncycastle.openpgp.operator.KeyFingerPrintCalculator;
import org.bouncycastle.util.Iterable;
import org.bouncycastle.util.Longs;
import org.bouncycastle.util.Strings;

/**
 * Often a PGP key ring file is made up of a succession of master/sub-key key rings.
 * If you want to read an entire public key file in one hit this is the class for you.
 */
public class PGPPublicKeyRingCollection
    implements Iterable<PGPPublicKeyRing>
{
    private Map<Long, PGPPublicKeyRing> pubRings = new HashMap<Long, PGPPublicKeyRing>();
    private List<Long> order = new ArrayList<Long>();

    private PGPPublicKeyRingCollection(
        Map<Long, PGPPublicKeyRing> pubRings,
        List<Long> order)
    {
        this.pubRings = pubRings;
        this.order = order;
    }

    public PGPPublicKeyRingCollection(
        byte[] encoding,
        KeyFingerPrintCalculator fingerPrintCalculator)
        throws IOException, PGPException
    {
        this(new ByteArrayInputStream(encoding), fingerPrintCalculator);
    }

    /**
     * Build a PGPPublicKeyRingCollection from the passed in input stream.
     *
     * @param in input stream containing data
     * @throws IOException if a problem parsing the base stream occurs
     * @throws PGPException if an object is encountered which isn't a PGPPublicKeyRing
     */
    public PGPPublicKeyRingCollection(
        InputStream in,
        KeyFingerPrintCalculator fingerPrintCalculator)
        throws IOException, PGPException
    {
        PGPObjectFactory pgpFact = new PGPObjectFactory(in, fingerPrintCalculator);
        Object obj;

        while ((obj = pgpFact.nextObject()) != null)
        {
            // Marker packets must be ignored
            if (obj instanceof PGPMarker)
            {
                continue;
            }
            if (!(obj instanceof PGPPublicKeyRing))
            {
                throw new PGPException(obj.getClass().getName() + " found where PGPPublicKeyRing expected");
            }

            PGPPublicKeyRing pgpPub = (PGPPublicKeyRing)obj;
            Long key = Longs.valueOf(pgpPub.getPublicKey().getKeyID());

            pubRings.put(key, pgpPub);
            order.add(key);
        }
    }

    public PGPPublicKeyRingCollection(
        Collection<PGPPublicKeyRing> collection)
    {
        Iterator<PGPPublicKeyRing> it = collection.iterator();

        while (it.hasNext())
        {
            PGPPublicKeyRing pgpPub = (PGPPublicKeyRing)it.next();

            Long key = Longs.valueOf(pgpPub.getPublicKey().getKeyID());

            pubRings.put(key, pgpPub);
            order.add(key);
        }
    }

    /**
     * Return the number of rings in this collection.
     *
     * @return size of the collection
     */
    public int size()
    {
        return order.size();
    }

    /**
     * return the public key rings making up this collection.
     */
    public Iterator<PGPPublicKeyRing> getKeyRings()
    {
        return pubRings.values().iterator();
    }

    /**
     * Return an iterator of the key rings associated with the passed in userID.
     *
     * @param userID the user ID to be matched.
     * @return an iterator (possibly empty) of key rings which matched.
     */
    public Iterator<PGPPublicKeyRing> getKeyRings(
        String userID)
    {
        return getKeyRings(userID, false, false);
    }

    /**
     * Return an iterator of the key rings associated with the passed in userID.
     * <p>
     *
     * @param userID       the user ID to be matched.
     * @param matchPartial if true userID need only be a substring of an actual ID string to match.
     * @return an iterator (possibly empty) of key rings which matched.
     */
    public Iterator<PGPPublicKeyRing> getKeyRings(
        String userID,
        boolean matchPartial)
    {
        return getKeyRings(userID, matchPartial, false);
    }

    /**
     * Return an iterator of the key rings associated with the passed in userID.
     * <p>
     *
     * @param userID       the user ID to be matched.
     * @param matchPartial if true userID need only be a substring of an actual ID string to match.
     * @param ignoreCase   if true case is ignored in user ID comparisons.
     * @return an iterator (possibly empty) of key rings which matched.
     */
    public Iterator<PGPPublicKeyRing> getKeyRings(
        String userID,
        boolean matchPartial,
        boolean ignoreCase)
    {
        Iterator<PGPPublicKeyRing> it = this.getKeyRings();
        List<PGPPublicKeyRing> rings = new ArrayList<PGPPublicKeyRing>();

        if (ignoreCase)
        {
            userID = Strings.toLowerCase(userID);
        }

        while (it.hasNext())
        {
            PGPPublicKeyRing pubRing = (PGPPublicKeyRing)it.next();
            Iterator<String> uIt = pubRing.getPublicKey().getUserIDs();

            while (uIt.hasNext())
            {
                String next = (String)uIt.next();
                if (ignoreCase)
                {
                    next = Strings.toLowerCase(next);
                }

                if (matchPartial)
                {
                    if (next.indexOf(userID) >= 0)
                    {
                        rings.add(pubRing);
                    }
                }
                else
                {
                    if (next.equals(userID))
                    {
                        rings.add(pubRing);
                    }
                }
            }
        }

        return rings.iterator();
    }

    /**
     * Return the PGP public key associated with the given key id.
     *
     * @param keyID if of the PGP public key
     * @return the PGP public key
     */
    public PGPPublicKey getPublicKey(
        long keyID)
    {
        Iterator<PGPPublicKeyRing> it = this.getKeyRings();

        while (it.hasNext())
        {
            PGPPublicKeyRing pubRing = (PGPPublicKeyRing)it.next();
            PGPPublicKey pub = pubRing.getPublicKey(keyID);

            if (pub != null)
            {
                return pub;
            }
        }

        return null;
    }

    /**
     * Return the public key ring which contains the key referred to by keyID.
     *
     * @param keyID key ID to match against
     * @return the public key ring
     */
    public PGPPublicKeyRing getPublicKeyRing(
        long keyID)
    {
        Long id = Longs.valueOf(keyID);

        if (pubRings.containsKey(id))
        {
            return (PGPPublicKeyRing)pubRings.get(id);
        }

        Iterator<PGPPublicKeyRing> it = this.getKeyRings();

        while (it.hasNext())
        {
            PGPPublicKeyRing pubRing = (PGPPublicKeyRing)it.next();
            PGPPublicKey pub = pubRing.getPublicKey(keyID);

            if (pub != null)
            {
                return pubRing;
            }
        }

        return null;
    }

    /**
     * Return the PGP public key associated with the given key fingerprint.
     *
     * @param fingerprint the public key fingerprint to match against.
     * @return the PGP public key matching fingerprint.
     */
    public PGPPublicKey getPublicKey(
        byte[] fingerprint)
    {
        Iterator<PGPPublicKeyRing> it = this.getKeyRings();

        while (it.hasNext())
        {
            PGPPublicKeyRing pubRing = (PGPPublicKeyRing)it.next();
            PGPPublicKey pub = pubRing.getPublicKey(fingerprint);

            if (pub != null)
            {
                return pub;
            }
        }

        return null;
    }

    /**
     * Return the PGP public key associated with the given key fingerprint.
     *
     * @param fingerprint the public key fingerprint to match against.
     * @return the PGP public key ring containing the PGP public key matching fingerprint.
     */
    public PGPPublicKeyRing getPublicKeyRing(
        byte[] fingerprint)
    {
        Iterator<PGPPublicKeyRing> it = this.getKeyRings();

        while (it.hasNext())
        {
            PGPPublicKeyRing pubRing = (PGPPublicKeyRing)it.next();
            PGPPublicKey pub = pubRing.getPublicKey(fingerprint);

            if (pub != null)
            {
                return pubRing;
            }
        }

        return null;
    }

    /**
     * Return any keys carrying a signature issued by the key represented by keyID.
     *
     * @param keyID the key id to be matched against.
     * @return an iterator (possibly empty) of PGPPublicKey objects carrying signatures from keyID.
     */
    public Iterator<PGPPublicKey> getKeysWithSignaturesBy(long keyID)
    {
        List<PGPPublicKey> keysWithSigs = new ArrayList<PGPPublicKey>();

        for (Iterator<PGPPublicKeyRing> it = this.iterator(); it.hasNext(); )
        {
            PGPPublicKeyRing k = (PGPPublicKeyRing)it.next();

            for (Iterator<PGPPublicKey> keyIt = k.getKeysWithSignaturesBy(keyID); keyIt.hasNext(); )
            {
                keysWithSigs.add(keyIt.next());
            }
        }

        return keysWithSigs.iterator();
    }

    /**
     * Return true if a key matching the passed in key ID is present, false otherwise.
     *
     * @param keyID key ID to look for.
     * @return true if keyID present, false otherwise.
     */
    public boolean contains(long keyID)
    {
        return getPublicKey(keyID) != null;
    }

    /**
     * Return true if a key matching the passed in fingerprint is present, false otherwise.
     *
     * @param fingerprint hte key fingerprint to look for.
     * @return true if keyID present, false otherwise.
     */
    public boolean contains(byte[] fingerprint)
    {
        return getPublicKey(fingerprint) != null;
    }

    public byte[] getEncoded()
        throws IOException
    {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();

        this.encode(bOut);

        return bOut.toByteArray();
    }

    public void encode(OutputStream outStream)
        throws IOException
    {
        BCPGOutputStream out = BCPGOutputStream.wrap(outStream);

        Iterator<Long> it = order.iterator();
        while (it.hasNext())
        {
            PGPPublicKeyRing sr = (PGPPublicKeyRing)pubRings.get(it.next());

            sr.encode(out);
        }
    }


    /**
     * Return a new collection object containing the contents of the passed in collection and
     * the passed in public key ring.
     *
     * @param ringCollection the collection the ring to be added to.
     * @param publicKeyRing  the key ring to be added.
     * @return a new collection merging the current one with the passed in ring.
     * @throws IllegalArgumentException if the keyID for the passed in ring is already present.
     */
    public static PGPPublicKeyRingCollection addPublicKeyRing(
        PGPPublicKeyRingCollection ringCollection,
        PGPPublicKeyRing publicKeyRing)
    {
        Long key = Longs.valueOf(publicKeyRing.getPublicKey().getKeyID());

        if (ringCollection.pubRings.containsKey(key))
        {
            throw new IllegalArgumentException("Collection already contains a key with a keyID for the passed in ring.");
        }

        Map<Long, PGPPublicKeyRing> newPubRings = new HashMap<Long, PGPPublicKeyRing>(ringCollection.pubRings);
        List<Long> newOrder = new ArrayList<Long>(ringCollection.order);

        newPubRings.put(key, publicKeyRing);
        newOrder.add(key);

        return new PGPPublicKeyRingCollection(newPubRings, newOrder);
    }

    /**
     * Return a new collection object containing the contents of this collection with
     * the passed in public key ring removed.
     *
     * @param ringCollection the collection the ring to be removed from.
     * @param publicKeyRing  the key ring to be removed.
     * @return a new collection not containing the passed in ring.
     * @throws IllegalArgumentException if the keyID for the passed in ring not present.
     */
    public static PGPPublicKeyRingCollection removePublicKeyRing(
        PGPPublicKeyRingCollection ringCollection,
        PGPPublicKeyRing publicKeyRing)
    {
        Long key = Longs.valueOf(publicKeyRing.getPublicKey().getKeyID());

        if (!ringCollection.pubRings.containsKey(key))
        {
            throw new IllegalArgumentException("Collection does not contain a key with a keyID for the passed in ring.");
        }

        Map<Long, PGPPublicKeyRing> newPubRings = new HashMap<Long, PGPPublicKeyRing>(ringCollection.pubRings);
        List<Long> newOrder = new ArrayList<Long>(ringCollection.order);

        newPubRings.remove(key);

        for (int i = 0; i < newOrder.size(); i++)
        {
            Long r = (Long)newOrder.get(i);

            if (r.longValue() == key.longValue())
            {
                newOrder.remove(i);
                break;
            }
        }

        return new PGPPublicKeyRingCollection(newPubRings, newOrder);
    }

    /**
     * Support method for Iterable where available.
     */
    public Iterator<PGPPublicKeyRing> iterator()
    {
        return new KeyRingIterator<PGPPublicKeyRing>(order, pubRings);
    }
}
