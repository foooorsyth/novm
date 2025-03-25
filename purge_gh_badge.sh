#!/bin/bash

# See: https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/about-anonymized-urls#removing-an-image-from-camos-cache
# Badge host has 12 hour cache: https://github.com/softwaremill/maven-badges/issues/445
# This must update first: https://maven-badges.herokuapp.com/sonatype-central/com.forsyth.novm/novm-runtime/badge.svg
# Then, this script can be run to purge Camo
curl -X PURGE $NOVM_GH_BADGE_URL