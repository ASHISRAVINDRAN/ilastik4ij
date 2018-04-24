package org.ilastik.ilastik4ij.hdf5;

import ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imglib2.RandomAccess;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import org.scijava.log.LogService;

import java.util.Arrays;

import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.*;
import static java.lang.Long.min;

public class Hdf5DataSetWriterFromImgPlus<T extends Type<T>> {
    private static final int NUM_OF_ARGB_CHANNELS = 4;
    private final ImgPlus<T> image;

    private final long nFrames;
    private final long nChannels;
    private final long nLevs;
    private long nRows;
    private long nCols;

    private LogService log;
    private String filename;
    private String dataset;
    private int compressionLevel;
    private final int cols;
    private final int rows;
    private boolean isFirstSlice;
    private int file_id = -1;
    private int dataspace_id = -1;
    private int dataset_id = -1;
    private int dcpl_id = -1;
    private final long[] maxdims = {
            HDF5Constants.H5S_UNLIMITED,
            HDF5Constants.H5S_UNLIMITED,
            HDF5Constants.H5S_UNLIMITED,
            HDF5Constants.H5S_UNLIMITED,
            HDF5Constants.H5S_UNLIMITED
    };

    public Hdf5DataSetWriterFromImgPlus(ImgPlus<T> image, String filename, String dataset, int compressionLevel, LogService log) {
        this.image = image;

        if (image.dimensionIndex(Axes.TIME) >= 0) {
            this.nFrames = image.dimension(image.dimensionIndex(Axes.TIME));
        } else {
            this.nFrames = 1;
        }
        if (image.dimensionIndex(Axes.CHANNEL) >= 0) {
            this.nChannels = image.dimension(image.dimensionIndex(Axes.CHANNEL));
        } else {
            this.nChannels = 1;
        }
        if (image.dimensionIndex(Axes.Z) >= 0) {
            this.nLevs = image.dimension(image.dimensionIndex(Axes.Z));
        } else {
            this.nLevs = 1;
        }
        if (image.dimensionIndex(Axes.X) < 0 || image.dimensionIndex(Axes.Y) < 0) {
            throw new IllegalArgumentException("image must have X and Y dimensions!");
        }

        this.nRows = image.dimension(image.dimensionIndex(Axes.Y));
        this.nCols = image.dimension(image.dimensionIndex(Axes.X));
        this.cols = Math.toIntExact(nCols); //One time cast from long to int data type.
        this.rows = Math.toIntExact(nRows); //One time cast from long to int data type.
        this.filename = filename;
        this.dataset = dataset;
        this.compressionLevel = compressionLevel;
        this.log = log;
        this.isFirstSlice = true;
    }

    public void write() {
        long[] chunk_dims = {1,
                min(nLevs, 256),
                min(nRows, 256),
                min(nCols, 256),
                1
        };
        log.info("Export Dimensions in tzyxc: " + String.valueOf(nFrames) + "x" + String.valueOf(nLevs) + "x"
                + String.valueOf(nRows) + "x" + String.valueOf(nCols) + "x" + String.valueOf(nChannels));

        try {

            file_id = H5.H5Fcreate(filename, H5F_ACC_TRUNC, H5P_DEFAULT, H5P_DEFAULT);
            dcpl_id = H5.H5Pcreate(H5P_DATASET_CREATE);
            H5.H5Pset_chunk(dcpl_id, 5, chunk_dims);
            H5.H5Pset_deflate(dcpl_id, compressionLevel);

            T val = image.firstElement();
            if (val instanceof UnsignedByteType) {
                log.info("Writing uint 8");
                writeIndividualChannels(H5T_NATIVE_UINT8);
            } else if (val instanceof UnsignedShortType) {
                log.info("Writing uint 16");
                writeIndividualChannels(H5T_NATIVE_UINT16);
            } else if (val instanceof UnsignedIntType) {
                log.info("Writing uint 32");
                writeIndividualChannels(H5T_NATIVE_UINT32);
            } else if (val instanceof FloatType) {
                log.info("Writing float 32");
                writeIndividualChannels(H5T_NATIVE_FLOAT);
            } else if (val instanceof ARGBType) {
                log.info("Writing ARGB to 4 uint8 channels");
                writeARGB();
            } else {
                log.error("Type Not handled yet!" + val.getClass());
                throw new IllegalArgumentException("Unsupported Type: " + val.getClass());
            }
        } catch (HDF5Exception err) {
            log.error("HDF5 API error occurred while creating '" + filename + "'." + err.getMessage());
            throw new RuntimeException(err);
        } catch (Exception err) {
            log.error("An unexpected error occurred while creating '" + filename + "'." + err.getMessage());
            throw new RuntimeException(err);
        } catch (OutOfMemoryError o) {
            log.error("Out of Memory Error while creating '" + filename + "'." + o.getMessage());
            throw new RuntimeException(o);
        } finally {
            H5.H5Sclose(dataspace_id);
            H5.H5Pclose(dcpl_id);
            H5.H5Dclose(dataset_id);
            H5.H5Fclose(file_id);
        }
    }

    private void writeARGB() {
        long[] channelDimsRGB = new long[5];
        channelDimsRGB[0] = nFrames; //t
        channelDimsRGB[1] = nLevs; //z
        channelDimsRGB[2] = nRows; //y
        channelDimsRGB[3] = nCols; //x
        channelDimsRGB[4] = NUM_OF_ARGB_CHANNELS;//c

        long[] colorIniDims = new long[5];
        colorIniDims[0] = 1;
        colorIniDims[1] = 1;
        colorIniDims[2] = nRows;
        colorIniDims[3] = nCols;
        colorIniDims[4] = 1;

        try {
            dataspace_id = H5.H5Screate_simple(5, colorIniDims, maxdims);
            dataset_id = H5.H5Dcreate(file_id, dataset, H5T_NATIVE_UINT8, dataspace_id, H5P_DEFAULT, dcpl_id, H5P_DEFAULT);
        } catch (HDF5Exception ex) {
            log.error("H5D dataspace creation failed." + ex.getMessage(), ex);
            throw new RuntimeException(ex);
        } catch (Exception err) {
            log.error("An error occurred at writeARGB method." + err.getMessage(), err);
            throw new RuntimeException(err);
        }

        @SuppressWarnings("unchecked")
        RandomAccess<ARGBType> rai = (RandomAccess<ARGBType>) image.randomAccess();
        boolean isAlphaChannelPresent = true;
        Object[][] pixelsByte;

        for (long t = 0; t < nFrames; t++) {
            if (image.dimensionIndex(Axes.TIME) >= 0)
                rai.setPosition(t, image.dimensionIndex(Axes.TIME));

            for (long z = 0; z < nLevs; z++) {
                if (image.dimensionIndex(Axes.Z) >= 0)
                    rai.setPosition(z, image.dimensionIndex(Axes.Z));

                if (nChannels == NUM_OF_ARGB_CHANNELS - 1) {
                    log.warn("Only 3 channel RGB found. Setting ALPHA channel to -1 (transparent).");
                    isAlphaChannelPresent = false;
                }

                for (long c = 0; c < NUM_OF_ARGB_CHANNELS; c++) {
                    // Construct 2D array of appropriate data
                    pixelsByte = new Byte[rows][cols];

                    if (!isAlphaChannelPresent) {
                        if (c == 0) {
                            for (Byte[] row : (Byte[][]) pixelsByte) {
                                Arrays.fill(row, (byte) -1);  // hard code alpha channel.
                            }
                        } else {
                            if (image.dimensionIndex(Axes.CHANNEL) >= 0) {
                                rai.setPosition(c - 1, image.dimensionIndex(Axes.CHANNEL));
                            }
                            fillStackSliceARGB(rai, pixelsByte);
                        }
                    } else {
                        if (image.dimensionIndex(Axes.CHANNEL) >= 0) {
                            rai.setPosition(c, image.dimensionIndex(Axes.CHANNEL));
                        }
                        fillStackSliceARGB(rai, pixelsByte);
                    }
                    // write it out
                    writeHDF5(H5T_NATIVE_UINT8, channelDimsRGB, colorIniDims, pixelsByte, t, z, c);
                }
            }
        }
        log.info("write uint8 RGB HDF5");
        log.info("compressionLevel: " + String.valueOf(compressionLevel));
        log.info("Done");
    }


    private void writeIndividualChannels(int hdf5DataType) {
        if (nLevs < 1) {
            log.error("got less than 1 z?");
            return;
        }
        long[] channel_Dims = new long[5];
        channel_Dims[0] = nFrames; // t
        channel_Dims[1] = nLevs; // z
        channel_Dims[2] = nRows; //y
        channel_Dims[3] = nCols; //x
        channel_Dims[4] = nChannels; // c

        long[] iniDims = new long[5];
        iniDims[0] = 1;
        iniDims[1] = 1;
        iniDims[2] = nRows;
        iniDims[3] = nCols;
        iniDims[4] = 1;

        try {
            dataspace_id = H5.H5Screate_simple(5, iniDims, maxdims);
            dataset_id = H5.H5Dcreate(file_id, dataset, hdf5DataType, dataspace_id, H5P_DEFAULT, dcpl_id, H5P_DEFAULT);
        } catch (HDF5Exception ex) {
            log.error("H5D dataspace creation failed." + ex.getMessage(), ex);
            throw new RuntimeException(ex); // or simple throw ex?
        } catch (Exception err) {
            log.error("An error occurred at writeIndividualChannels method." + err.getMessage(), err);
            throw new RuntimeException(err);
        }

        RandomAccess<T> rai = image.randomAccess();
        Object[][] pixelSlice;

        for (long t = 0; t < nFrames; t++) {
            if (image.dimensionIndex(Axes.TIME) >= 0)
                rai.setPosition(t, image.dimensionIndex(Axes.TIME));

            for (long z = 0; z < nLevs; z++) {
                if (image.dimensionIndex(Axes.Z) >= 0)
                    rai.setPosition(z, image.dimensionIndex(Axes.Z));

                for (long c = 0; c < nChannels; c++) {
                    if (image.dimensionIndex(Axes.CHANNEL) >= 0)
                        rai.setPosition(c, image.dimensionIndex(Axes.CHANNEL));

                    // Construct 2D array of appropriate data type.
                    if (hdf5DataType == H5T_NATIVE_UINT8) {
                        pixelSlice = new Byte[rows][cols];
                    } else if (hdf5DataType == H5T_NATIVE_UINT16) {
                        pixelSlice = new Short[rows][cols];
                    } else if (hdf5DataType == H5T_NATIVE_UINT32) {
                        pixelSlice = new Integer[rows][cols];
                    } else if (hdf5DataType == H5T_NATIVE_FLOAT) {
                        pixelSlice = new Float[rows][cols];
                    } else {
                        throw new IllegalArgumentException("Trying to save dataset of unknown datatype.");
                    }
                    fillStackSliceARGB(rai, pixelSlice);
                    // write HDF5
                    writeHDF5(hdf5DataType, channel_Dims, iniDims, pixelSlice, t, z, c);
                }
            }
        }

        log.info("Done writing the hdf5");
    }

    private void writeHDF5(int hdf5DataType, long[] channelDimsRGB, long[] colorIniDims, Object[][] pixelsByte, long t, long z, long c) {
        if (isFirstSlice) {
            writeFirstSlice(hdf5DataType, pixelsByte, channelDimsRGB);
            isFirstSlice = false;
        } else {
            long[] start = {t, z, 0, 0, c};
            writeHyperslabs(hdf5DataType, pixelsByte, start, colorIniDims);
        }
    }

    private <E> void writeFirstSlice(int hdf5DataType, E[][] pixelsSlice, long[] channelDimsRGB) {
        try {
            H5.H5Dwrite(dataset_id, hdf5DataType, H5S_ALL, H5S_ALL, H5P_DEFAULT, pixelsSlice);
            H5.H5Dset_extent(dataset_id, channelDimsRGB);
        } catch (HDF5Exception e) {
            log.error("Error while writing first chunk." + e.getMessage(), e);
            throw new RuntimeException(e);
        } catch (Exception e) {
            log.error("Error writing HDF5 at writeFirstSlice method." + e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            H5.H5Sclose(dataspace_id);//check if close is necessary
        }
    }

    private <E> void writeHyperslabs(int hdf5DataType, E[][] pixelsByte, long[] start, long[] colorIniDims) {
        try {
            dataspace_id = H5.H5Dget_space(dataset_id);
            H5.H5Sselect_hyperslab(dataspace_id, HDF5Constants.H5S_SELECT_SET, start, null, colorIniDims, null);
            int memSpace = H5.H5Screate_simple(5, colorIniDims, null);
            H5.H5Dwrite(dataset_id, hdf5DataType, memSpace, dataspace_id, H5P_DEFAULT, pixelsByte);
        } catch (HDF5Exception e) {
            log.error("Error while writing extended hyperslabs." + e.getMessage(), e);
            throw new RuntimeException(e);
        } catch (Exception e) {
            log.error("An error occurred at writeHyperslabs method." + e.getMessage(), e);
            throw new RuntimeException(e);
        }

    }

    @SuppressWarnings({"unchecked", "TypeParameterHidesVisibleType"})
    private <E, T> void fillStackSliceARGB(RandomAccess<T> rai, E[][] pixelArray) {

        for (int x = 0; x < cols; x++) {
            rai.setPosition(x, image.dimensionIndex(Axes.X));
            for (int y = 0; y < rows; y++) {
                rai.setPosition(y, image.dimensionIndex(Axes.Y));
                T value = rai.get();
                if (value instanceof UnsignedByteType) {
                    pixelArray[y][x] = (E) (Byte) (new Integer(((UnsignedByteType) value).get()).byteValue());
                } else if (value instanceof UnsignedShortType) {
                    pixelArray[y][x] = (E) (Short) (new Integer((((UnsignedShortType) value).get())).shortValue());
                } else if (value instanceof UnsignedIntType) {
                    pixelArray[y][x] = (E) (Integer) (new Long((((UnsignedIntType) value).get())).intValue());
                } else if (value instanceof FloatType) {
                    pixelArray[y][x] = (E) (new Float((((FloatType) value).get())));
                } else if (value instanceof ARGBType) {
                    pixelArray[y][x] = (E) (Byte) (new Integer(((ARGBType) value).get()).byteValue());
                } else {
                    log.error("Type Not handled yet!");
                    // throw Exception here???
                }
            }
        }
    }


}
