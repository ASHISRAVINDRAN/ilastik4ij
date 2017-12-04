# ilastik ImageJ modules

[![Build Status](https://travis-ci.org/ilastik/ilastik4ij.svg?branch=master)](https://travis-ci.org/ilastik/ilastik4ij)

_(c) Carsten Haubold, Image Analysis and Learning Lab, HCI/IWR, University of Heidelberg._

This repository contains ImageJ2 plugins that wrap ilastik workflows for usage in [ImageJ](https://imagej.net) 
and [KNIME](https://www.knime.com). Data transfer is managed through temporary HDF5 file export/import, which can also be performed individually.
The ilastik workflows are invoked by running the ilastik headless mode from the command line.

Currently, three workflows are wrapped: Pixel classification, Object classification and tracking. 
There is one additional setting showing up in the ImageJ menu, which configures the location of the ilastik binary.

## Contents

* [Installation](#installation)
* [User documentation](#user-documentation)
    - [General](#general)
    - [Import](#import)
    - [Export](#export)
    - [ilastik configuration for the workflow wrappers](#configuration)
    - [Pixel Classification](#pixel-classification)
    - [Object Classification](#object-classification)
    - [Tracking](#tracking)
    - [Usage in KNIME](#usage-in-knime)
* [Developer documentation](#developer-documentation)
    - [Deployment](#deployment)

## Installation <a href="installation"></a>

Within ImageJ/Fiji you can install the plugin via the `Help -> Update` menu and select the `ilastik Import Export` site.

**Note**: The plugins need Java 1.8, if you see error messages popping up that might be caused by an older Java version.

## User documentation <a href="user-documentation"></a>

The ilastik workflow wrappers, as well as importer and exporter, can be found in ImageJ under `Plugins -> ilastik`, 
or in KNIME in the `Community Contributions -> KNIME Image Processing -> ImageJ2 -> Plugins -> ilastik`.

![ImageJ Menu](./doc/screenshots/IJ-Menu.png)

### General <a href="general"></a>

All Plugins output status information to log files, so we suggest to keep an eye at the ImageJ `Windows -> Console`.

All workflow wrappers have the option to produce only the input files, so that you can use those to train an ilastik project. **TODO** write more!

### Import <a href="import"></a>

Found at `Plugins -> ilastik -> Import HDF5`.

HDF5 files can contain multiple datasets. Thus, when you import an HDF5 file containing more than one dataset, 
you will have to select which dataset you want to import. Then, you will be presented with the following dialog:

![ImageJ Menu](./doc/screenshots/IJ-Import.png)

where you should select or enter the correct meaning of the different dimensions of the dataset. 
At least `x` and `y` must be present, and the number of characters (`x`,`y`,`z` spatial, `c` channels and `t` time)
must be equal to the number of dimensions listed in the description above.

If you choose `Load and apply LUT`, after loading the `3-3-2-RGB` lookup table will be applied for you, which is
a sensible option for connected component labellings (e.g. a tracking result) or per-object predictions.

### Export <a href="export"></a>

Found at `Plugins -> ilastik -> Export HDF5`.

If you want to save the currently opened image to a HDF5 file that can be immediately opened in ilastik,
use this export option. Additionally to the location where the file should be saved, you will be asked how much
the dataset should be compressed. Use `0` for raw data because it doesn't compress well, but `9` for segmentations etc, 
where many pixel values are equal. See also the tooltip when you hover over `Compression Level`.

![ImageJ Menu](./doc/screenshots/IJ-Export.png)

### ilastik configuration of the workflow wrappers <a href="configuration"></a>
Found at `Plugins -> ilastik -> Configure ilastik executable location`.

![configuration dialog](./doc/screenshots/IJ-Config.png)

* Path to ilastik executable: choose the location of your ilastik binary executable
* Number of threads to use (-1 for no limit)
* Specify an upper bound of RAM that ilastik is allowed to use

### Pixel Classification <a href="pixel-classification"></a>
Found at `Plugins -> ilastik -> Run Pixel Classification Prediction`.

![Pixel Classification Dialog](./doc/screenshots/IJ-PC-dialog.png)

**Inputs:** 

* a raw image on which to run the pixel classification (if only one is opened, there is no selection in the dialog) 
  ![Pixel Classification Input](./doc/screenshots/IJ-PC-input.png)
* a project file
* whether to produce per-pixel probabilities, or a segmentation

**Output:**

* if _Probabilities_ was selected: a multi-channel float image that you can _e.g._ threshold to obtain a 
  segmentation ![Pixel Classification Output: Probabilities](./doc/screenshots/IJ-PC-predictions.png)
* or a _Segmentation_:a single-channel image where each pixel gets a value corresponding to an _object ID_ inside a _connected component_. 
  ![Pixel Classification Output: Segmentation](./doc/screenshots/IJ-PC-segmentation.png)

### Object Classification <a href="object-classification"></a>
Found at `Plugins -> ilastik -> Run Object Classification Prediction`.

![Object Classification Dialog](./doc/screenshots/IJ-OC-dialog.png)

**Inputs:** 

* a project file
* one raw image (select the appropriate one in the dropdown box as shown above)
* one additional image that contains either per-pixel probabilities or a segmentation
* select the appropriate input type (_Probabilities_ or _Segmentation_)

**Output:**

* a new image where the pixels of each object get assigned the value that corresponds to the class that was predicted for this object. 
  ![Object Classification Output](./doc/screenshots/IJ-OC-output.png)

### Tracking <a href="tracking"></a>
Found at `Plugins -> ilastik -> Run Tracking`.

![Tracking Dialog](./doc/screenshots/IJ-Track-dialog.png)

**Inputs:** 

* a project file
* one raw image (with a time axis!) ![Tracking Raw Input](./doc/screenshots/IJ-Track-inputRaw.png)
* one additional image that contains either per-pixel probabilities or a segmentation with the same dimensions as the raw image. 
  ![Tracking Segmentation Input](./doc/screenshots/IJ-Track-inputSeg.png)
* select the appropriate input type (_Probabilities_ or _Segmentation_)

**Output:**

* a new image stack where the pixels of each object in each frame get assigned the value that
  corresponds to the _lineage ID_ of the tracked object. Whenever an object enters the field of view
  it will be assigned a new _lineage ID_. All descendants of this object will be assigned the same
  _lineage ID_. ![Tracking Output](./doc/screenshots/IJ-Track-output.png)

### Usage in KNIME <a href="usage-in-knime"></a>

![KNIME Workflow](./doc/screenshots/KNIME.png)

## Developer documentation <a href="developer-documentation"></a>

The workflow wrappers are ImageJ2 plugins (see https://imagej.net/Writing_plugins), annotated with
`@Plugin` for automated discovery by the _scijava_ plugin architecture, and derived from `Command`
to be an executable item. Each command can have multiple `@Parameter`s, which are to be provided by
the user in an auto-generated GUI (see https://imagej.net/Script_Parameters for a list of which
parameters are allowed). One can have multiple `Dataset`s as input parameters, but the output should
be an `ImgPlus` (an ImageJ2 datastructure wrapping an [ImgLib2](https://imagej.net/ImgLib2) `Img`
with metadata) so that the result pops up as new window. A `Dataset` is a wrapper around an
`ImgPlus`.

**Attention:** there are ImageJ 1 and ImageJ 2 containers for images. In ImageJ 1, images were stored
as `ImagePlus`, containing `ImageProcessors` to access the underlying data. We try to use ImageJ 2
containers everywhere which are roughly wrapped as `Dataset > ImgPlus > Img >
RandomAccessibleInterval`.

**Testing:** no real tests are included right now, but you can run the `main` method in
`WorkflowTests.java` which fires up an ImageJ instance for each of the three plugins.

### Deployment <a href="deployment"></a>

We follow the setup of other `scijava` modules and use their Travis setup that allows us to
automatically deploy Maven artifacts (built modules) as explained [here](https://imagej.net/Travis).
The project configuration, including dependencies, is contained in the `pom.xml` file. To create a
new release, use the `release-version.sh` script from https://github.com/scijava/scijava-scripts,
which goes a long way of ensuring that the project is ready to be published. Once it is released,
the nightly build of KNIME Image Processing (KNIP) will pick it up and wrap it as well.
